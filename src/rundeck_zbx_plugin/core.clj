;;
;; This namespace should  remain free of Rundeck Classes  and stick to
;; Clojure conventions.  Because you will definitely want to test some
;; core  functionality  outside  of  Rundeck. Maybe  you  should  also
;; implement a CLI that returns Rundeck Resources in YAML form ...
;;
;; Because  of  this  it  is  OK refer  require  this  namespace  from
;; nodes.clj but not the other way around.
;;
(ns rundeck-zbx-plugin.core
  (:require [proto-zabbix.api :as api]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :as pp])
  (:gen-class))

;;
;; Tags are returned from Zabbix API as an array of tag/value pairs:
;;
;;     :tags [{:tag "Tag", :value ""}
;;            {:tag "Key", :value "Value"}
;;            {:tag "Key", :value "Another Value"}]
;;
;; Tags in  Rundeck is a HashSet<String>  so that each of  these pairs
;; will need to be compacted into a single string:
;;
(defn- find-tags [zabbix-host]
  (set
   (for [{:keys [tag value]} (:tags zabbix-host)]
     (if (= "" value)
       tag
       (str tag "=" value)))))

;;
;; Groups come out from API like this:
;;
;;     :groups [{:groupid "4", :name "Zabbix servers",
;;               :internal "0", :flags "0"}]
;;
;; We will present them as Tags to Rundeck.
;;
(defn- find-groups [zabbix-host]
  (set (map :name (:groups zabbix-host))))

;;
;; This  is how  the interfaces  are  returned from  Zabbix API.  Main
;; interface is marked with :main "1":
;;
;;     :interfaces [{:ip "127.0.0.1", :useip "0", :hostid "10095",
;;                   :interfaceid "5", :type "1", :port "10050", :details
;;                   [], :dns "localhost", :main "1"}]
;;
(defn- find-interface [zabbix-host]
  (let [interfaces (:interfaces zabbix-host)
        [one] (get (group-by :main interfaces) "1" [nil])]
    (if (= "1" (:useip one))
      (:ip one)
      (:dns one))))

;; We dont need to strip unused  fields, only for brevity. Some fields
;; are expected by Rundeck will need to be added though ...
(defn- make-host [properties zabbix-host]
  (let [keep-fields [:host :name :description
                     :hostid :status :available
                     :maintenance_status #_:interfaces]
        slim-host (select-keys zabbix-host keep-fields)
        ;; NOTE:  some   assumptions  about   Zabbix  URLs   are  made
        ;; here. Below we will just append a hostid to the base-url:
        base-url (str/replace (:url properties)
                              #"/api_jsonrpc.php"
                              "/hosts.php?form=update&hostid=")]
    ;; Keep some Zabbix fields, augment with Rundeck fields:
    (-> slim-host
        ;; These two are obligatory:
        (assoc :nodename (:host zabbix-host))
        (assoc :username "root")
        ;; Rundeck convention ist to call it hostname, even it is an IP:
        (assoc :hostname (find-interface zabbix-host))
        ;; Here be  dragons: "tags" is  both a string attribute  and a
        ;; separate  array-valued field  of  a Rundeck  Node. In  some
        ;; places/constructors a  list of strings is  accepted.  FWIW,
        ;; we define tags as a set of strings. There will be no way to
        ;; tell in Rundeck  it a host belongs to host  group "Prod" or
        ;; was rather tagged with "Prod".
        (assoc :tags (set/union (find-tags zabbix-host)
                                (find-groups zabbix-host)))
        ;;
        ;; The field  "editUrl" is used  to link the host  to external
        ;; site [1]:
        ;;
        ;;     Specifies  a URL  to  a remote  site  which will  allow
        ;;     editing of the Node.  When specified, the Node resource
        ;;     will  display an  "Edit" link  in the  Rundeck GUI  and
        ;;     clicking it will open a new browser page for the URL.
        ;;
        ;; [1] https://docs.rundeck.com/docs/administration/projects/resource-model-sources/resource-editor.html#resource-editor
        ;;
        (assoc :editUrl (str base-url (:hostid zabbix-host))))))

;;
;; Properties should supply at least these fields:
;;
;;   {:url "https://zabbix.example.com/api_jsonrpc.php"
;;    :user "user"
;;    :password "password"
;;    :host-group "Zabbix servers"}
;;
(defn- do-query [properties]
  (let [config (select-keys properties [:url :user :password])
        zbx (api/make-zbx config)
        ;; The  API  call  host.get  needs groupids  it  you  want  to
        ;; restrict  the output  to  a  few host  groups.   This is  a
        ;; dictionaty to translate names to such groupids:
        group-dict (into {} (for [g (zbx "hostgroup.get")]
                              [(:name g) (:groupid g)]))
        ;; What do we  do if the name is not  found? De-facto an empty
        ;; list of hosts is returned in this case:
        host-group (get properties :host-group)
        groupid (get group-dict host-group)
        ;; Force lazy sequences, see logout below:
        hosts (doall
               (zbx "host.get"
                    {:groupids [groupid]
                     :selectInterfaces "extend"
                     :selectGroups "extend"
                     :selectTags "extend"}))]
    (zbx "user.logout")
    ;; Maybe we  should implement  taking ranges  of hosts?  Like with
    ;; offest and limit in SQL?
    #_hosts
    (for [host hosts]
      ;; The editUrl can only be derived from properties:
      (make-host properties host))))

;; NOTE: Passwords may leak here because  we add exception data to the
;; message text.   Rundeck and Leiningen  only show the  message, this
;; makes troubleshooting  difficult. In this namespace  properties are
;; expected  to have  keywords like  :url, :user,  :password, etc.  as
;; keys.
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
