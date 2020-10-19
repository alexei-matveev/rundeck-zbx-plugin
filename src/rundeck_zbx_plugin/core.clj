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

;; We dont  need to strip  unused fields  down, only for  brevity when
;; printing. Some fields are expected by Rundeck too ...
(defn- make-host [zabbix-host]
  (let [slim-host (select-keys zabbix-host
                               [:host :name :description #_:interfaces])]
    ;; Kepp Zabbix fields, augment with Rundeck fields:
    (-> slim-host
        ;; Rundeck convention ist to call it hostname, even it is an IP:
        (assoc :hostname (main-interface zabbix-host))
        ;; These two are obligatory:
        (assoc :nodename (:host zabbix-host))
        (assoc :user "root"))))

;;
;; Properties should supply at least these fields:
;;
;;   {"url" "https://zabbix.example.com/api_jsonrpc.php"
;;    "user" "user"
;;    "password" "password"
;;    "host-group" "Zabbix servers"}
;;
(defn do-query [properties]
  (let [config {:url (get properties "url")
                :user (get properties "user")
                :password (get properties "password")}
        zbx (api/make-zbx config)
        ;; The  API  call  host.get  needs groupids  it  you  want  to
        ;; restrict  the output  to  a  few host  groups.   This is  a
        ;; dictionaty to translate names to such groupids:
        group-dict (into {} (for [g (zbx "hostgroup.get")]
                              [(:name g) (:groupid g)]))
        ;; What do we  do if the name is not  found? De-facto an empty
        ;; list of hosts is returned in this case:
        host-group (get properties "host-group")
        groupid (get group-dict host-group)
        ;; Force lalzy sequences, see logout below:
        hosts (doall
               (zbx "host.get"
                    {:groupids [groupid]
                     :selectInterfaces "extend"}))]
    (zbx "user.logout")
    ;; Maybe we  should implement  taking ranges  of hosts?  Like with
    ;; offest and limit in SQL?
    (map make-host hosts)))

;; NOTE: Passwords may leak here ...  Add data to the message, Rundeck
;; and  Leiningen only  show the  message, this  makes troubleshooting
;; difficult.
(defn query [properties]
  (try
    (do-query properties)
    (catch Exception e
      (let [data (ex-data e)]
        (throw (ex-info (str (ex-message e) " Data: " data) data e))))))

;; For  testing purposes  read  properties  from a  file  and run  the
;; query.  Eventually we  should format  the output  as Yaml/Json  for
;; Rundeck to parse.
(defn -main [path]
  (let [properties (edn/read-string (slurp path))
        zabbix-hosts (query properties)]
    (pp/pprint zabbix-hosts)))
