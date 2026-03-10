IB_WRAP_LOCAL ?= $(HOME)/Syncthing/dev/clojure/ib-cl-wrap

.PHONY: help vendor-update vendor-status

help:
	@echo "Targets:"
	@echo "  vendor-update   Pull latest ib-cl-wrap from local clone or GitHub"
	@echo "  vendor-status   Show submodule divergence at a glance"

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
