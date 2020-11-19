#### rundeck-zbx-plugin: Get Nodes from Zabbix

Assuming you already installed the Token in your local config:

    $ export KUBECONFIG=~/.kube/config
    $ kubectl get nodes
    $ source <(kubectl completion bash)

Install Rundeck in Kubernetes

    $ kubectl create namespace hello-rundeck
    $ kubectl config set-context --current --namespace=hello-rundeck
    $ kubectl apply -k k3s/

Then visit the [URL](https://rundeck.localhost). You may consider
deploying Zabbix from the
[hello-zabbix](https://github.com/alexei-matveev/hello-zabbix) Repo
and configure the source with the Cluster URL:

    http://zabbix-frontend.hello-zabbix.svc.cluster.local/api_jsonrpc.php

#### Installation

Build it:

    $ lein uberjar

Install it into libext:

    $ kubectl cp target/uberjar/rundeck-zbx-plugin-0.1.0.jar hello-rundeck/rundeck-server-xxx:/home/rundeck/libext/
    $ kubectl exec rundeck-server-xxx -- ls -l /home/rundeck/libext

For other instances the path may differ, eventually use SSH to deploy:

    $ scp target/uberjar/rundeck-zbx-plugin-0.1.0.jar root@rundeck.example.com:/var/lib/rundeck/libext/

Or just upload the JAR file in GUI if you are into it.

#### License

Copyright © 2020 <alexei.matveev@gmail.com>

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
