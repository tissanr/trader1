(ns trader1.auth-test
  (:require [clojure.test :refer [deftest is testing]]
            [buddy.hashers :as hashers]
            [clojure.edn :as edn]
            [trader1.auth :as auth]))

(def ^:private fixture-file "test/fixtures/test-auth.edn")

;; Known hash for "testpass", generated with (auth/hash-password "testpass")
(def ^:private known-hash
  "bcrypt+sha512$497f47c733e5f886382c80bd5948bec8$12$b7521a6e83ab97988fa24cb161bb148d4f61187fec626a6d")

(def ^:private fake-users
  {:users [{:username "admin"  :password-hash known-hash}
           {:username "viewer" :password-hash known-hash}]})

;; --- load-users ---

(deftest load-users-test
  (with-redefs [auth/auth-config-file fixture-file]
    (testing "returns a map with :users key"
      (let [result (auth/load-users)]
        (is (map? result))
        (is (contains? result :users))))
    (testing ":users is a non-empty sequence"
      (is (seq (:users (auth/load-users)))))
    (testing "each user has :username and :password-hash"
      (doseq [user (:users (auth/load-users))]
        (is (contains? user :username))
        (is (contains? user :password-hash))))))

;; --- find-user ---

(deftest find-user-test
  (with-redefs [auth/load-users (fn [] fake-users)]
    (testing "returns the user map for a known username"
      (let [user (auth/find-user "admin")]
        (is (some? user))
        (is (= "admin" (:username user)))))
    (testing "returns nil for an unknown username"
      (is (nil? (auth/find-user "nobody"))))
    (testing "returns the correct user when multiple users exist"
      (is (= "viewer" (:username (auth/find-user "viewer")))))))

;; --- authenticate ---

(deftest authenticate-test
  (with-redefs [auth/load-users (fn [] fake-users)]
    (testing "returns user map on correct credentials"
      (let [user (auth/authenticate "admin" "testpass")]
        (is (some? user))
        (is (= "admin" (:username user)))))
    (testing "returns nil for wrong password"
      (is (nil? (auth/authenticate "admin" "wrongpass"))))
    (testing "returns nil for unknown username"
      (is (nil? (auth/authenticate "ghost" "testpass"))))
    (testing "returns nil for empty password"
      (is (nil? (auth/authenticate "admin" ""))))))

;; --- needs-setup? ---

(def ^:private placeholder-users
  {:users [{:username "admin" :password-hash "REPLACE_WITH_BCRYPT_HASH"}]})

(deftest needs-setup-test
  (testing "returns true when file is missing"
    (with-redefs [auth/auth-config-file "test/fixtures/nonexistent.edn"]
      (is (true? (auth/needs-setup?)))))
  (testing "returns true when placeholder hash is present"
    (with-redefs [auth/load-users (fn [] placeholder-users)]
      (is (true? (auth/needs-setup?)))))
  (testing "returns false when a real hash is in place"
    (with-redefs [auth/load-users (fn [] fake-users)]
      (is (false? (auth/needs-setup?))))))

;; --- write-config! ---

(deftest write-config-test
  (let [tmp-file (str (System/getProperty "java.io.tmpdir")
                      "/trader1-test-auth-" (System/currentTimeMillis) ".edn")]
    (try
      (with-redefs [auth/auth-config-file tmp-file]
        (testing "writes a parseable EDN file"
          (auth/write-config! "testuser" "testpass")
          (let [contents (edn/read-string (slurp tmp-file))]
            (is (map? contents))
            (is (= 1 (count (:users contents))))
            (is (= "testuser" (:username (first (:users contents)))))))
        (testing "needs-setup? returns false after write-config!"
          (is (false? (auth/needs-setup?))))
        (testing "authenticate succeeds with the written credentials"
          (let [user (auth/authenticate "testuser" "testpass")]
            (is (some? user))
            (is (= "testuser" (:username user)))))
        (testing "authenticate fails with wrong password"
          (is (nil? (auth/authenticate "testuser" "wrongpass")))))
      (finally
        (clojure.java.io/delete-file tmp-file true)))))

;; --- hash-password ---

(deftest hash-password-test
  (testing "produces a string hash"
    (is (string? (auth/hash-password "somepass"))))
  (testing "hash verifies against the original plaintext"
    (let [h (auth/hash-password "mypassword")]
      (is (hashers/check "mypassword" h))))
  (testing "hash does not verify against a different password"
    (let [h (auth/hash-password "mypassword")]
      (is (not (hashers/check "otherpassword" h)))))
  (testing "two hashes of the same password differ (salted)"
    (let [h1 (auth/hash-password "same")
          h2 (auth/hash-password "same")]
      (is (not= h1 h2)))))
