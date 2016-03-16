package org.bimserver.buildingstorey;
   
import org.bimserver.interfaces.objects.SInternalServicePluginConfiguration;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.models.store.ObjectDefinition;
import org.bimserver.models.store.ServiceDescriptor;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.models.store.Trigger;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.PluginContext;
import org.bimserver.plugins.services.ServicePlugin;
import org.bimserver.shared.exceptions.PluginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildingStoreyCheckServicePlugin extends ServicePlugin
{

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildingStoreyCheckServicePlugin.class);
	private static final String NAMESPACE = "http://bimserver.org/buildingStoreyCheck";
	
	@Override
	public void init(PluginContext pluginContext) throws PluginException {
		super.init(pluginContext);
	}

	@Override
	public void register(long uoid, SInternalServicePluginConfiguration internalServicePluginConfiguration, final PluginConfiguration pluginConfiguration) 
	{
		ServiceDescriptor buildingStoreyCheck = StoreFactory.eINSTANCE.createServiceDescriptor();
		buildingStoreyCheck.setProviderName("BIMserver");
		buildingStoreyCheck.setIdentifier("" + internalServicePluginConfiguration.getOid());
		buildingStoreyCheck.setName("BuildingStorey");
		buildingStoreyCheck.setDescription("BuildingStorey");
		buildingStoreyCheck.setNotificationProtocol(AccessMethod.INTERNAL);
		buildingStoreyCheck.setReadRevision(true);
		buildingStoreyCheck.setWriteExtendedData(NAMESPACE); 
		buildingStoreyCheck.setTrigger(Trigger.NEW_REVISION);
		
		registerNewRevisionHandler(uoid, buildingStoreyCheck, new BuilldingStoreyNewRevisionHandler());
	}

	@Override
	public String getTitle() {
		return "BuildingStoreCheck";
	}

	@Override
	public ObjectDefinition getSettingsDefinition() {
		ObjectDefinition objectDefinition = StoreFactory.eINSTANCE.createObjectDefinition();
		return objectDefinition;
	}

	@Override
	public void unregister(SInternalServicePluginConfiguration internalService) {
	}
	
	
}