(ns rejax-demo.core
  (:require
    [goog.dom :as dom]
    [reagent.core :as reagent]
    [re-frame.core :refer [dispatch dispatch-sync clear-subscription-cache! subscribe reg-sub]]


(reg-sub
  :app
  identity)


(defn- main
  []
  (let [db-ref (subscribe [:app])]
    (fn []
      [:div.container])))

(defn- mount-root!
  [app-element]
  (reagent/render [main] app-element))


(defn ^:export run
  []
  (let [app-element (dom/getElement "app")]
    (when (nil? app-element)
      (js/error "Missing 'app' element for mounting!"))
    (clear-subscription-cache!)
    (mount-root! app-element)))
