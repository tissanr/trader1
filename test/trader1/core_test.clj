(ns trader1.core-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [trader1.core :refer [get-path post-form-path throw-if-err]]))

(deftest throw-if-err-test
  (testing "no error — empty error vector returns nil"
    (is (nil? (throw-if-err {:error []}))))
  (testing "with error — throws Exception"
    (is (thrown? Exception (throw-if-err {:error ["some error"]}))))
  (testing "exception message includes the error"
    (is (thrown-with-msg? Exception #"EAPI"
          (throw-if-err {:error ["EAPI:Invalid key"]})))))

(deftest get-path-test
  (testing "concatenates url and path and passes :as :json"
    (let [calls (atom [])]
      (with-redefs [client/get (fn [url opts]
                                 (swap! calls conj {:url url :opts opts})
                                 {:status 200 :body {:result "ok"}})]
        (let [result (get-path "https://example.com" "/foo/bar")]
          (is (= 1 (count @calls)))
          (is (= "https://example.com/foo/bar" (:url (first @calls))))
          (is (= :json (get-in (first @calls) [:opts :as])))
          (is (= {:status 200 :body {:result "ok"}} result)))))))

(deftest post-form-path-test
  (testing "passes url, form-params, headers and :as :json to client/post"
    (let [calls (atom [])]
      (with-redefs [client/post (fn [url opts]
                                  (swap! calls conj {:url url :opts opts})
                                  {:status 200 :body {:result "posted"}})]
        (let [form   {:nonce "12345" :pair "XBTUSD"}
              headers {"API-Key" "testkey" "API-Sign" "testsign"}
              result (post-form-path "https://example.com/private/Balance" form headers)]
          (is (= 1 (count @calls)))
          (is (= "https://example.com/private/Balance" (:url (first @calls))))
          (is (= form (get-in (first @calls) [:opts :form-params])))
          (is (= headers (get-in (first @calls) [:opts :headers])))
          (is (= :json (get-in (first @calls) [:opts :as])))
          (is (= {:status 200 :body {:result "posted"}} result)))))))
