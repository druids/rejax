(ns rejax.core
  "Maintains requests activity in the DB (keeps information about running HTTP requests)."
  (:require
   [clojure.string :refer [blank?]]
   [ajax.core :as ajax]
   [ajax.transit :as transit]
   [cognitect.transit :as ct]
   [re-frame.core :as re-frame]))


(defrecord ^{:doc "A Ring-like response object"}
  Response [status body headers])


(defmulti ->status type)

(defmethod ->status cljs.core.IVector
  [response-vec]
  (let [[_ _ status] response-vec]
    status))

(defmethod ->status Response
  [response-obj]
  (:status response-obj))

(defmethod ->status :default
  [_]
  nil)


(defn xhr->ring-req
  "Converts a XHR response into Ring-like request object"
  [reader xhr]
  (->Response
   (.getStatus xhr)
   (when-not (-> xhr .getResponseText blank?)
     (ct/read reader (.getResponseText xhr)))
   (-> xhr
       .getResponseHeaders
       (js->clj :keywordize-keys true))))


(defn better-transit-response-format
  "Works same as `ajax.core/transit-response-format` except it converts a response into Ring-like request object"
  ([]
   (better-transit-response-format {}))
  ([opts]
   (let [conf (transit/transit-response-format opts)
         reader (or (:reader opts)
                    (ct/reader :json opts))]
     (assoc conf :read (partial xhr->ring-req reader)))))


(defn request
  "Works like `ajax.core/ajax-request`, but it passes `rejax.core.Response` for both success and failure"
  [opts]
  (-> opts
      (update :handler (fn [handler]
                         (fn [[_ response]]
                           (handler (if (contains? response :failure) ;; failure reponse is wrapped in `:failure` key
                                      (:response response)
                                      response)))))
      ajax/ajax-request))


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
  (re-frame/enrich
    (fn [db [_ {:keys [id]}]]
      (toggle-waiting db id true))))


(def ^{:doc "Use this middleware when you finish a request with a given `id`."}
  finish-waiting
  (re-frame/enrich
    (fn [db [_ {:keys [id]}]]
      (toggle-waiting db id false))))


(def ^{:doc "Toggles offline state be a response's status"}
  toggle-offline-status
  (re-frame/enrich
    (fn [db [_ _ response-vec-or-obj]]
      (if-let [status (->status response-vec-or-obj)]
        (assoc-in db [:rejax :offline?] (zero? status))
        db))))


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
                 :or {error-handler #(re-frame/dispatch [:rejax/handle-error-response {} %])}}]
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
