(ns trader1.auth
  (:require [clojure.edn :as edn]
            [buddy.hashers :as hashers]))

(def auth-config-file "config/auth.edn")

(defn load-users
  "Reads user config from config/auth.edn.
  Expected shape: {:users [{:username \"admin\" :password-hash \"bcrypt+sha512$...\"}]}"
  []
  (edn/read-string (slurp auth-config-file)))

(defn find-user
  "Returns the user map for the given username, or nil if not found."
  [username]
  (->> (:users (load-users))
       (filter #(= (:username %) username))
       first))

(defn authenticate
  "Returns the user map if username and password are valid, else nil."
  [username password]
  (when-let [user (find-user username)]
    (when (hashers/check password (:password-hash user))
      user)))

(defn hash-password
  "Produces a bcrypt+sha512 hash of a plaintext password.
  Use this once in the REPL to generate the hash for config/auth.edn."
  [plaintext]
  (hashers/derive plaintext {:alg :bcrypt+sha512}))

(def ^:private placeholder-hash "REPLACE_WITH_BCRYPT_HASH")

(defn needs-setup?
  "Returns true when config/auth.edn is missing or still contains the
  placeholder hash written by bootstrap-config."
  []
  (try
    (boolean (some #(= placeholder-hash (:password-hash %))
                   (:users (load-users))))
    (catch java.io.FileNotFoundException _ true)))

(defn write-config!
  "Hashes password and writes a new config/auth.edn with a single user.
  Overwrites any existing file."
  [username password]
  (spit auth-config-file
        (pr-str {:users [{:username      username
                          :password-hash (hash-password password)}]})))
