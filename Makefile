IB_WRAP_LOCAL ?= $(HOME)/Syncthing/dev/clojure/ib-cl-wrap
BASE ?= main
BRANCH := $(shell git branch --show-current)

.DEFAULT_GOAL := help

.PHONY: help all install deps bootstrap-config frontend-build frontend-watch backend dev test vendor-update vendor-status

help:
	@echo "Targets:"
	@echo "  make install        Bootstrap a local dev setup from a clean checkout"
	@echo "  make deps           Install project dependencies and initialize submodules"
	@echo "  make frontend-build Build the browser bundle once"
	@echo "  make frontend-watch Run the CLJS compiler in watch mode"
	@echo "  make backend        Start the backend server"
	@echo "  make dev            Print the recommended two-terminal dev workflow"
	@echo "  make test           Run the backend test suite"
	@echo "  make                Alias for 'make frontend-build'"
	@echo "  make vendor-status  Show vendor/ib-cl-wrap divergence"
	@echo "  make vendor-update  Update vendor/ib-cl-wrap from local clone or GitHub"

all: frontend-build

deps:
	git submodule update --init --recursive
	npm install
	lein deps

bootstrap-config:
	@if [ ! -f config/auth.edn ]; then \
	  cp config/auth.edn.example config/auth.edn; \
	  echo "Created config/auth.edn from example"; \
	else \
	  echo "Keeping existing config/auth.edn"; \
	fi
	@if [ ! -f config/settings.edn ]; then \
	  cp config/settings.edn.example config/settings.edn; \
	  echo "Created config/settings.edn from example"; \
	else \
	  echo "Keeping existing config/settings.edn"; \
	fi
	@if [ ! -f credentials/kraken.key ]; then \
	  cp credentials/kraken.key.example credentials/kraken.key; \
	  echo "Created credentials/kraken.key from example"; \
	else \
	  echo "Keeping existing credentials/kraken.key"; \
	fi
	@echo ""
	@echo "Next manual steps:"
	@echo "  - put your real Kraken API key/secret into credentials/kraken.key"
	@echo "  - update config/auth.edn with a real user/password hash"
	@echo "  - place the IB API jar at lib/ibapi.jar if it is not already there"

frontend-build: deps
	npx shadow-cljs compile app

frontend-watch:
	npx shadow-cljs watch app

backend:
	lein run

dev:
	@echo "Terminal 1:"
	@echo "  make frontend-watch"
	@echo ""
	@echo "Terminal 2:"
	@echo "  make backend"
	@echo ""
	@echo "Then open http://localhost:3000"

test:
	lein test

install: deps bootstrap-config frontend-build test
	@echo ""
	@echo "Local setup complete."
	@echo "For day-to-day development run:"
	@echo "  make frontend-watch"
	@echo "  make backend"

## Update vendor/ib-cl-wrap to the latest commit.
## Uses local clone (IB_WRAP_LOCAL) if it exists, otherwise fetches from GitHub.
vendor-update:
	@if [ -d "$(IB_WRAP_LOCAL)/.git" ]; then \
	  echo "Fetching from local clone: $(IB_WRAP_LOCAL)"; \
	  git -C vendor/ib-cl-wrap fetch "$(IB_WRAP_LOCAL)"; \
	else \
	  echo "Fetching from GitHub origin"; \
	  git -C vendor/ib-cl-wrap fetch origin; \
	fi
	git -C vendor/ib-cl-wrap checkout FETCH_HEAD
	@echo ""
	@echo "New HEAD: $$(git -C vendor/ib-cl-wrap rev-parse --short HEAD) $$(git -C vendor/ib-cl-wrap log -1 --pretty=%s)"
	git add vendor/ib-cl-wrap
	@echo ""
	@echo "Staged. Review with 'git diff --cached' then commit:"
	@echo "  git commit -m \"Bump vendor/ib-cl-wrap: <description>\""

## Show what commits the vendor is ahead/behind ib-cl-wrap.
vendor-status:
	@echo "=== vendor/ib-cl-wrap ==="
	@git -C vendor/ib-cl-wrap log --oneline -5
	@echo ""
	@if [ -d "$(IB_WRAP_LOCAL)/.git" ]; then \
	  git -C vendor/ib-cl-wrap fetch --quiet "$(IB_WRAP_LOCAL)" 2>/dev/null; \
	  AHEAD=$$(git -C vendor/ib-cl-wrap rev-list FETCH_HEAD..HEAD --count); \
	  BEHIND=$$(git -C vendor/ib-cl-wrap rev-list HEAD..FETCH_HEAD --count); \
	  echo "vs ib-cl-wrap local: $$AHEAD ahead, $$BEHIND behind"; \
	  if [ "$$BEHIND" -gt 0 ]; then \
	    echo ""; \
	    echo "New upstream commits:"; \
	    git -C vendor/ib-cl-wrap log --oneline HEAD..FETCH_HEAD; \
	  fi \
	fi
