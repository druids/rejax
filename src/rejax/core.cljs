(ns rejax.core
  "Maintains requests activity in the DB (keeps information about running HTTP requests)."
  (:require
    [clojure.string :refer [blank?]]
    [ajax.core :as ajax]
    [re-frame.core :refer [dispatch enrich]]))


(defn db->waiting?
  "Returns `true` if a given `id` is waiting for a response."
  [db id]
  (true? (get-in db [:rejax :waiting? id])))


(defn db->offline?
  "Returns `true` if an app is offline."
  [db]
  (-> db :rejax :offline? true?))


(defn toggle-waiting
  [db id value]
  (assoc-in db [:rejax :waiting? id] value))


(def ^{:doc "Use this middleware when you start a request with a given `id`."}
  start-waiting
  (enrich
    (fn [db [_ {:keys [id]}]]
      (toggle-waiting db id true))))


(def ^{:doc "Use this middleware when you finish a request with a given `id`."}
  finish-waiting
  (enrich
    (fn [db [_ {:keys [id]}]]
      (toggle-waiting db id false))))


(def ^{:doc "Toggles offline state be a response's status"}
  toggle-offline-status
  (enrich
    (fn [db [_ _ [response headers status]]]
      (assoc-in db [:rejax :offline?] (zero? status)))))


(def initial-http-params
  {:headers {:accept "application/json"}
   :format :json
   :response-format {:read identity, :description "raw"}
   :keywords? true})


(defn- xhrio->response-vec
  "Takes a pure `goog.net.XhrIO` objects and returns it's response as a vector with a response and headers.
   Both items are CLJS maps."
  [xhrio]
  (let [expected-statuses #{200 201 400 401}
        response (if (and (contains? expected-statuses (.getStatus xhrio))
                          (-> xhrio .getResponseText blank? not))
                   (-> xhrio .getResponseJson (js->clj :keywordize-keys true))
                   (.getResponseText xhrio))]
    [response (-> xhrio .getResponseHeaders (js->clj :keywordize-keys true))]))


(defn- xhrio->vec
  [xhrio]
  (cond
    (nil? xhrio) [nil nil 0]
    (zero? (.getStatus xhrio)) [nil nil 0]
    (blank? (.getResponseText xhrio)) [nil
                                       (-> xhrio
                                           .getResponseHeaders
                                           (js->clj :keywordize-keys true))
                                       (.getStatus xhrio)]
    :else (conj (xhrio->response-vec xhrio) (.getStatus xhrio))))


(defn- wrap-handlers
  "Split xhr object into [response headers] for handlers."
  [handler error-handler kwargs]
  (-> kwargs
      (assoc :handler #(handler (xhrio->vec %)))
      (assoc :error-handler #(error-handler (xhrio->vec (:response %))))))


(defn- call-http-method
  [method url & {:keys [handler error-handler headers format response-format keywords? params]
                 :as kwargs
                 :or {error-handler #(dispatch [:rejax/handle-error-response {} %])}}]
  (apply method (->> kwargs
                     (merge initial-http-params)
                     (wrap-handlers handler error-handler)
                     (into [])
                     flatten
                     (concat [url]))))


(def GET (partial call-http-method ajax/GET))
(def POST (partial call-http-method ajax/POST))
(def PUT (partial call-http-method ajax/PUT))
(def DELETE (partial call-http-method ajax/DELETE))
