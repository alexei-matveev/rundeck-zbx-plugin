/*
 * Copyright 2012 DTO Labs, Inc. (http://dtolabs.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
* ExampleStepPlugin.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 11/9/12 4:09 PM
* 
*/

// The same namespace as in the clojure  code. That is a choice, not a
// requirement:
package rundeck_clj_plugin;

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.dtolabs.rundeck.plugins.util.PropertyBuilder;

import java.util.Map;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

/**
 * ExampleStepPlugin demonstrates a basic StepPlugin, and how to
 * programmatically build all of the plugin's Properties exposed in
 * the Rundeck GUI.
 *
 * The plugin class is annotated with @Plugin to define the service
 * and name of this service provider plugin.
 *
 * The provider name of this plugin statically defined in the
 * class. The service name makes use of ServiceNameConstants to
 * provide the known Rundeck service names.
 *
 * Original Author: Greg Schueler <greg@dtosolutions.com>.
 */
@Plugin(name = ExampleStepPlugin.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class ExampleStepPlugin implements StepPlugin, Describable {
    /**
     * Define a name used to identify your plugin. It is a good idea
     * to use a fully qualified package-style name.
     */
    public static final String SERVICE_PROVIDER_NAME = "rundeck_clj_plugin.ExampleStepPlugin";

    public static Description myGetDescription () {
        //
        // Please do not ask. I do not quite get it. Rundeck does some
        // non-trivial staff with the class files, jars, and the class
        // loader so  that Clojure  ist not  able to  bootstrap itself
        // without this voodoo [1]:
        //
        //     Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        //
        // [1] https://groups.google.com/forum/#!msg/clojure/Aa04E9aJRog/f0CXZCN1z0AJ
        //
        Thread.currentThread()
            .setContextClassLoader (ExampleStepPlugin.class.getClassLoader());
        IFn require = Clojure.var ("clojure.core", "require");
        require.invoke (Clojure.read ("rundeck-clj-plugin.core"));

        return (Description) Clojure.var ("rundeck-clj-plugin.core", "hello").invoke();
    }

    /**
     * Overriding this method gives the plugin a chance to take part in building the {@link
     * com.dtolabs.rundeck.core.plugins.configuration.Description} presented by this plugin.  This subclass can use the
     * {@link DescriptionBuilder} to modify all aspects of the description, add or remove properties, etc.
     *
     * @see ExampleRemoteScriptNodeStepPlugin ExampleRemoteScriptNodeStepPlugin for an example that defines properties
     *      using annotations such that the property values will be automatically bound to the plugin class instance
     *      fields
     */
    public Description getDescription() {
        //
        // Trying to call Clojure from here without the voodo with the
        // Class   Loader,  see   hello(),   will   fail  at   loading
        // clojure/core__init.class --- the loader  just does not find
        // it.
        //
        return myGetDescription();

        /*
        return DescriptionBuilder.builder()
            .name(SERVICE_PROVIDER_NAME)
            .title("Example Step")
            .description("Does nothing")
            .property(PropertyBuilder.builder()
                          .string("bunny")
                          .title("Bunny")
                          .description("Bunny name")
                          .required(true)
                          .build()
            )
            .property(PropertyBuilder.builder()
                          .booleanType("lampkin")
                          .title("Lampkin")
                          .description("Want Lampkin?")
                          .required(false)
                          .defaultValue("false")
                          .build()
            )
            .property(PropertyBuilder.builder()
                          .freeSelect("color")
                          .title("Color")
                          .description("Your color")
                          .required(false)
                          .defaultValue("Blue")
                          .values("Blue", "Beige", "Black")
                          .build()
            )
            .property(PropertyBuilder.builder()
                          .integer("many")
                          .title("Many")
                          .description("How many?")
                          .required(false)
                          .defaultValue("2")
                          .build()
            )
            .property(PropertyBuilder.builder()
                          .longType("cramp")
                          .title("Cramp")
                          .description("How crampy more?")
                          .required(false)
                          .defaultValue("20")
                          .build()
            )
            .property(PropertyBuilder.builder()
                          .select("rice")
                          .title("Rice Cream")
                          .description("Rice Cream Flavor")
                          .required(false)
                          .values("Flambe", "Crambo")
                          .build()
            )
            .build();
        */
    }

    /**
     * This enum lists the known reasons this plugin might fail
     */
    static enum Reason implements FailureReason{
        ExampleReason
    }

    /**
     * Here is the meat of the plugin implementation, which should perform the appropriate logic for your plugin.
     * <p/>
     * The {@link PluginStepContext} provides access to the appropriate Nodes, the configuration of the plugin, and
     * details about the step number and context.
     */
    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws
                                                                                                      StepException{
        System.out.println("Example step executing on nodes: " + context.getNodes().getNodeNames());
        System.out.println("Example step configuration: " + configuration);
        System.out.println("Example step num: " + context.getStepNumber());
        System.out.println("Example step context: " + context.getStepContext());
        if ("true".equals(configuration.get("lampkin"))) {
            throw new StepException("lampkin was true", Reason.ExampleReason);
        }
    }
}
