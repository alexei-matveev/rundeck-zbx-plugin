;;
;; This namespace  should remain free  of Rundeck specifics.  You will
;; definitely  wanto  to  test  some  core  funcitonality  outside  of
;; Rundeck. Maybe you should also implement a CLI ...
;;
(ns rundeck-zbx-plugin.core
  (:require [proto-zabbix.api :as api])
  (:gen-class))

;;
;; Config should be like:
;;
;;   {:url "https://zabbix.example.com/api_jsonrpc.php"
;;    :user "user"
;;    :password "password"}
;;
(defn query [properties]
  (let [config {:url (get properties "url")
                :user (get properties "user")
                :password (get properties "password")}
        zbx (api/make-zbx config)]
    (take 3 (zbx "host.get"))))

(defn -main [& args]
  (println "Hello from Rundeck Plugin CLI!"))
