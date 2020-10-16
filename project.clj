(defproject rundeck-zbx-plugin "0.1.0-SNAPSHOT"
  :description "Experimental Rundeck Plugin"
  :url "https://github.com/alexei-matveev/hello-rundeck"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [f0bec0d/proto-zabbix "0.2.0-SNAPSHOT"]]
  ;; Having  one  source path  contain  the  other may  cause  obscure
  ;; problems, so say  Leiningen docs.  So Clojure stays  in ./src and
  ;; Java goes here.
  :java-source-paths ["java"]
  :main ^:skip-aot rundeck-zbx-plugin.core
  :target-path "target/%s"
  ;;
  ;; Rundeck Core Classes are provided  by Framework --- do no include
  ;; them  in  your  Uberjar.   The  version  here  should  better  be
  ;; compatible  with that  in  k3s  Deployment entsprechen.   Consult
  ;; Docker Hub  and the Developer  manual [1]. Documentation  may lag
  ;; behind, so check Maven directly [2]:
  ;;
  ;; [1] https://docs.rundeck.com/docs/developer/01-plugin-development.html#java-plugin-development
  ;; [2] https://search.maven.org/artifact/org.rundeck/rundeck-core
  ;;
  :profiles {:provided {:dependencies
                        [[org.rundeck/rundeck-core "3.3.4-20201007"]]}
             :uberjar {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  ;; https://docs.rundeck.com/docs/developer/01-plugin-development.html#java-plugin-development
  :jar-name "rundeck-zbx-plugin-0.1.0.jar"
  :uberjar-name "rundeck-zbx-plugin-0.1.0.jar"
  :manifest ~{"Rundeck-Plugin-Version" "1.2"
              "Rundeck-Plugin-Archive" "true"
              ;; Comma-separated. FWIW, the rundeck_zbx_plugin.core
              ;; didnt quite work, see below:
              "Rundeck-Plugin-Classnames" "rundeck_zbx_plugin.HelloNodes"
              ;; Space-separated:
              "Rundeck-Plugin-Libs" ""
              ;; "Class-Path" ""
              "Rundeck-Plugin-Author" "f0bec0d"
              "Rundeck-Plugin-URL" "https://github.com/alexei-matveev/hello-rundeck"
              "Rundeck-Plugin-Date" (.format (java.text.SimpleDateFormat. "yyyy-MM-dd")
                                             (java.util.Date.))
              "Rundeck-Plugin-File-Version" (.format (java.text.SimpleDateFormat. "yyyyMMddHHmm")
                                                     (java.util.Date.))})

;;
;; See the discussion of class loaders on Slack:
;;
;;     https://clojurians-log.clojureverse.org/clojure/2020-02-03
;;
;; Even  with  a  Java  shim   as  in  ExampleStepPlugin  loading  the
;; clojure/core stub fails with:
;;
;;    Could not locate clojure/core__init.class, ...
;;
;; It is  just that  the call  chain that  leads to  this clojure/core
;; "stub"  ist started  bei  initializing clojure.java.api.Clojure  in
;; this Java Code:
;;
;;    IFn require = Clojure.var ("clojure.core", "require");
;;
;; This is from the middle of the call stack:
;;
;; Caused by: java.io.FileNotFoundException: Could not locate clojure/core__init.class, clojure/core.clj or clojure/core.cljc on classpath.
