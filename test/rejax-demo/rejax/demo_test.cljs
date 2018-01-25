(ns rejax.demo-test
  (:require
    [cljs.test :refer-macros [async deftest testing is]]
    [goog.dom :as dom]
    [rejax-demo.core :refer [mount-root!]]))


(deftest demo-test
  (testing "should simulate HTTP request"
    (async done
      (let [body (.-body (dom/getDocument))
            app-el (dom/createElement "div" (clj->js {:id "app"}))
            _ (dom/appendChild body app-el)
            _ (-> (mount-root! app-el))
            btn (dom/getElement "submit-request")]
        (.click btn)
        ;; a spinner should be shown
        (js/setTimeout #(= (some? (.querySelector app-el "i.fa"))) 500)
        ;; a spinner should be gone
        (js/setTimeout #(do
                          (= (nil? (.querySelector app-el "i.fa")))
                          (println (.-innerHTML btn))
                          (done))
                       3500)))))

