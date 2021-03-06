package org.elixir_lang.iex.mix.configuration

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP
import com.intellij.openapi.extensions.Extensions
import org.elixir_lang.icons.ElixirIcons
import javax.swing.Icon

class Type : com.intellij.execution.configurations.ConfigurationType {
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(Factory)
    override fun getConfigurationTypeDescription(): String = "Starts an Interactive Elixir (IEx) Console using the Mix project"
    override fun getIcon(): Icon = ElixirIcons.IEX_MIX
    override fun getId(): String = "org.elixir_lang.iex.mix.configuration.Type"
    override fun getDisplayName(): String = "IEx Mix"

    companion object {
        internal fun getInstance(): Type = Extensions.findExtension(CONFIGURATION_TYPE_EP, Type::class.java)
    }
}
