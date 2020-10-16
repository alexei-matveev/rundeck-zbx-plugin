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

;; Main interface is marked with :main "1":
(comment
  (let [fake-host {:interfaces [{:ip "127.0.0.1",
                                 :useip "0",
                                 :hostid "10095",
                                 :interfaceid "5",
                                 :type "1",
                                 :port "10050",
                                 :details [],
                                 :dns "localhost",
                                 :main "1"}]}]
    (main-interface fake-host)))

(defn- main-interface [zabbix-host]
  (let [interfaces (:interfaces zabbix-host)
        [one] (get (group-by :main interfaces) "1" [nil])]
    (if (= "1" (:useip one))
      (:ip one)
      (:dns one))))

(defn- make-host [zabbix-host]
  (let [slim-host (select-keys zabbix-host
                               [:host :name :description #_:interfaces])
        host-address (main-interface zabbix-host)]
    ;; Rundeck convention ist to call it hostname, even it is an IP:
    (assoc slim-host :hostname host-address)))

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
        ;; Force eval, see user.logout below:
        zabbix-hosts (doall
                      (zbx "host.get" {:selectInterfaces "extend"}))
        hosts (map make-host zabbix-hosts)]
    ;; Dont forget to logout:
    (zbx "user.logout")
    (take 5 hosts)))

;; For  testing purposes  read  properties  from a  file  and run  the
;; query.  Eventually we  should format  the output  as Yaml/Json  for
;; Rundeck to parse.
(defn -main [path]
  (let [properties (edn/read-string (slurp path))
        zabbix-hosts (query properties)]
    (pp/pprint zabbix-hosts)))
