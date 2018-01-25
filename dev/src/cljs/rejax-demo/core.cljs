(ns rejax-demo.core
  (:require
    [goog.dom :as dom]
    [reagent.core :as reagent]
    [re-frame.core :refer [after dispatch dispatch-sync clear-subscription-cache! subscribe reg-sub reg-event-db]]
    [rejax.core :as rejax]))


(reg-sub
  :app
  identity)


(def fake-request
  (after
    (fn [db [_ rejax-map]]
      (js/setTimeout #(dispatch [:rejax-demo/fake-response rejax-map [{:name "Foo"} {:x-header "foo"} 200]]) 3000))))


(reg-event-db
  :rejax-demo/fake-request
  [fake-request rejax/start-waiting]
  (fn [db [_ {:keys [id]}]]
    db))


(reg-event-db
  :rejax-demo/fake-response
  [rejax/finish-waiting]
  (fn [db [_ rejax-map [response headers status]]]
    (assoc db
           :fake-response response
           :fake-headers headers
           :fake-status status)))


(defn demo
  [db]
  (let [request-id :demo
        waiting? (rejax/db->waiting? db request-id)
        response (:fake-response db)
        headers (:fake-headers db)
        status (:fake-status db)]
    [:div.demo
     [:button {:class "btn btn-primary"
               :id "submit-request"
               :on-click #(dispatch [:rejax-demo/fake-request {:id request-id}])}
      "simulate request " (when waiting? [:i {:class "fa fa-spin fa-gear"}])]
     [:ul
      [:li "Fake response: " (pr-str response)]
      [:li "Fake headers: " (pr-str headers)]
      [:li "Fake status: " (pr-str status)]]]))


(defn main
  []
  (let [db-ref (subscribe [:app])]
    (fn []
      [:div.container
       [demo @db-ref]])))


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
