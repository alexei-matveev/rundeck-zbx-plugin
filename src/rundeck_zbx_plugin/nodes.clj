;;
;; See HelloNodes.java for the Java Shim ...
;;
;; A Node in  Rundeck is a glorified map  of atributes HashMap<String,
;; String> and  an extra HashSet  of string-valued tags all  buried in
;; the Java MBean  legacy [1].  Node Set is internally  a TreeMap from
;; node  names  to  such   Nodes  TreeMap<String,  INodeEntry>  albeit
;; obscured by its  custom set of accessors hiding  the actual TreeMap
;; behind an INodeSet of NodeSetImpl [2].
;;
;; Attributes  as a  *persistent* Map  does not  allow Put-Operations,
;; thus if  you try to  .setTags after .setAttributes you  will notice
;; it. Setting tags  has namely the additional effect  of inserting an
;; attribute "tags" which  is a comma separated list  derived from the
;; set.   Though  here  we  appear  to  overwrite  that  list  with  a
;; subsequent .setAttributes.  Go figure.   Do not use the *attribute*
;; "tags".  FWIW, the order in the example is reversed [3].
;;
;; For e reasonable example of a Resource Model Source Plugin see "EC2
;; Nodes Plugin" [4].
;;
;; [1] https://github.com/rundeck/rundeck/blob/main/core/src/main/java/com/dtolabs/rundeck/core/common/NodeEntryImpl.java
;; [2] https://github.com/rundeck/rundeck/blob/main/core/src/main/java/com/dtolabs/rundeck/core/common/NodeSetImpl.java
;; [3] https://github.com/rundeck/rundeck/blob/development/examples/json-plugin/src/main/java/com/dtolabs/rundeck/plugin/resources/format/json/JsonResourceFormatParser.java
;; [4] https://github.com/rundeck-plugins/rundeck-ec2-nodes-plugin
;;
(ns rundeck-zbx-plugin.nodes
  (:require [rundeck-zbx-plugin.core :as core])
  (:import
   (com.dtolabs.rundeck.plugins.util DescriptionBuilder PropertyBuilder)
   (com.dtolabs.rundeck.core.common INodeEntry NodeEntryImpl NodeSetImpl)
   (com.dtolabs.rundeck.core.resources ResourceModelSource)))

;; See more example Properties in step.clj and/or Rundeck Docs.
(defn get-description [name]
  (println "get-description: building description ...")
  (-> (DescriptionBuilder/builder)
      (.name name)
      (.title "Zabbix Hosts")
      (.description "Provides Zabbix hosts as Rundeck resources")
      (.property (-> (PropertyBuilder/builder)
                     (.string "url")
                     (.title "Zabbix API URL")
                     (.description "API URL ending with api_jsonrpc.php")
                     (.defaultValue "https://localhost/api_jsonrpc.php")
                     (.required true)
                     (.build)))
      (.property (-> (PropertyBuilder/builder)
                     (.string "user")
                     (.title "Zabbix user")
                     (.description "Should have sufficient access to read hosts.")
                     (.defaultValue "Admin")
                     (.required true)
                     (.build)))
      (.property (-> (PropertyBuilder/builder)
                     (.string "password")
                     (.title "Zabbix password")
                     (.description "Maybe this should not be your personal password?")
                     (.defaultValue "zabbix")
                     (.renderingAsPassword)
                     (.required true)
                     (.build)))
      (.property (-> (PropertyBuilder/builder)
                     (.string "host-group")
                     (.title "Host group")
                     (.description "Identifies the group of hosts")
                     (.required true)
                     (.build)))
      (.property (-> (PropertyBuilder/builder)
                     (.string "user-name")
                     (.title "SSH user name")
                     (.description "Node resources must specify a user name")
                     (.defaultValue "root")
                     (.required true)
                     (.build)))
      (.build)))

;; Rundeck HashMap  keys must be  strings.  Note that  (str :nodename)
;; were ":nodename" not "nodename".
(defn- stringify-keys [dict]
  (into {} (for [[k v] dict] [(name k) v])))

;; Rest of the Clojure World uses keywords as keys:
(defn- keywordize-keys [dict]
  (into {} (for [[k v] dict] [(keyword k) v])))

(defn- make-node [host]
  (let [attr (dissoc host :tags)
        tags (:tags host)]
    (doto (NodeEntryImpl.)
      ;; Should better be a mutable HashSet:
      (.setTags tags)
      ;; Should better be a mutable HashMap:
      (.setAttributes (stringify-keys attr)))))

;; You will  probably want to move  the core functionality out  of the
;; namespaces "tainted" by  Rundeck Classes, even if  just for testing
;; from the CLI or such.
(defn- make-nodes [properties]
  (let [zabbix-hosts (core/query (keywordize-keys properties))
        ;; This default "root" cannot  possibly apply, right?  Rundeck
        ;; will  supply "user-name"  as specified  in the  GUI or  the
        ;; default from the above PropertyBuilder, wont it?
        user-name (get properties "user-name" "root")]
    (for [host zabbix-hosts]
      ;; The key should be spelled "username", not "user":
      (make-node (assoc host :username user-name)))))

(defn create-resource-model-source [properties]
  (println "create-resource-model-source: building resource model source ...")
  (println {:properties properties})
  (reify ResourceModelSource
    ;; Should return an INodeSet:
    (getNodes [_]
      (println "getNodes: just a few in a HashMap ...")
      ;; Note sure  if the  keys are ever  used, generate  some random
      ;; symbols:
      (let [nodes (into {}
                        (for [^INodeEntry n (make-nodes properties)]
                          #_[(.getNodename n) n]
                          [(str (gensym)) n]))]
        (NodeSetImpl. (java.util.HashMap. ^java.util.Map nodes))))))

