package blog.peterobrien.jdbc.autorest;

import java.util.Locale;

import jakarta.inject.Named;
import oracle.dbtools.plugin.api.conf.ConfigurationSetting;
import oracle.dbtools.plugin.api.di.annotations.Provides;
import oracle.dbtools.plugin.api.i18n.Translatable;

@Provides
public abstract class AutoRESTSettings {
	static final String AUTOREST_API_DOC = "autorest.api.doc.file";
	@Named(AutoRESTSettings.AUTOREST_API_DOC)
	static final ConfigurationSetting _AUTOREST_API_DOC = ConfigurationSetting.setting("openapi.yaml", new Translatable() {

		@Override
		public String toString(Iterable<Locale> arg0) {
			return "The location of the OpenAPI v3 description of AutoREST services.";
		}});
}
