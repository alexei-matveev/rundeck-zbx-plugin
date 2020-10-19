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

;;
;; Tags are returned from Zabbix API as an array of tag/value pairs:
;;
;;     :tags [{:tag "Tag", :value ""}
;;            {:tag "Key", :value "Value"}
;;            {:tag "Key", :value "Another Value"}]
;;
;; In Rundeck  each of these  pairs will need  to be collapsed  into a
;; single string. So be it:
;;
(defn- find-tags [zabbix-host]
  (for [{:keys [tag value]} (:tags zabbix-host)]
    (if (= "" value)
      tag
      (str tag "=" value))))

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
    (find-interface fake-host)))

(defn- find-interface [zabbix-host]
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
    ;; Keep some Zabbix fields, augment with Rundeck fields:
    (-> slim-host
        ;; These two are obligatory:
        (assoc :nodename (:host zabbix-host))
        (assoc :user "root")
        ;; Rundeck convention ist to call it hostname, even it is an IP:
        (assoc :hostname (find-interface zabbix-host))
        ;; Here be  dragons: "tags" is  both a string attribute  and a
        ;; separate array-valued  field of a Rundeck  Node.  Still, we
        ;; keep them as a list here:
        (assoc :tags (find-tags zabbix-host)))))

;;
;; Properties should supply at least these fields:
;;
;;   {"url" "https://zabbix.example.com/api_jsonrpc.php"
;;    "user" "user"
;;    "password" "password"
;;    "host-group" "Zabbix servers"}
;;
(defn- do-query [properties]
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
        ;; Force lazy sequences, see logout below:
        hosts (doall
               (zbx "host.get"
                    {:groupids [groupid]
                     :selectInterfaces "extend"
                     :selectTags "extend"}))]
    (zbx "user.logout")
    ;; Maybe we  should implement  taking ranges  of hosts?  Like with
    ;; offest and limit in SQL?
    #_hosts
    (map make-host hosts)))

;; NOTE: Passwords may leak here because  we add exception data to the
;; message text.  Rundeck and  Leiningen only  show the  message, this
;; makes troubleshooting difficult.
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
