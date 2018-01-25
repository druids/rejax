(ns rejax-demo.dev
  (:require
    [figwheel.client :as fw]
    [re-frisk.core :refer [enable-re-frisk!]]
    [rejax-demo.core :as core]))

(enable-console-print!)
(enable-re-frisk!)

(fw/start {:on-jsload core/run
           :websocket-url "ws://localhost:3449/figwheel-ws"})
