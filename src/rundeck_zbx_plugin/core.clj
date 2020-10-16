;;
;; This namespace  should remain free  of Rundeck specifics.  You will
;; definitely  wanto  to  test  some  core  funcitonality  outside  of
;; Rundeck. Maybe you should also implement a CLI ...
;;
(ns rundeck-zbx-plugin.core
  (:require [proto-zabbix.api :as api]
            [clojure.edn :as edn]
            [clojure.pprint :as pp])
  (:gen-class))

(defn- make-host [zabbix-host]
  (select-keys zabbix-host
               [:host :name :description]))
;;
;; Properties should supply at least these fields:
;;
;;   {"url" "https://zabbix.example.com/api_jsonrpc.php"
;;    "user" "user"
;;    "password" "password"}
;;
(defn query [properties]
  (let [config {:url (get properties "url")
                :user (get properties "user")
                :password (get properties "password")}
        zbx (api/make-zbx config)
        zabbix-hosts (zbx "host.get")
        hosts (map make-host zabbix-hosts)]
    (take 3 hosts)))

;; For  testing purposes  read  properties  from a  file  and run  the
;; query.  Eventually we  should format  the output  as Yaml/Json  for
;; Rundeck to parse.
(defn -main [path]
  (let [properties (edn/read-string (slurp path))
        zabbix-hosts (query properties)]
    (pp/pprint zabbix-hosts)))
