package org.bimserver.BuildingStorey;
 
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SActionState;
import org.bimserver.interfaces.objects.SExtendedData;
import org.bimserver.interfaces.objects.SExtendedDataSchema;
import org.bimserver.interfaces.objects.SFile;
import org.bimserver.interfaces.objects.SInternalServicePluginConfiguration;
import org.bimserver.interfaces.objects.SLongActionState;
import org.bimserver.interfaces.objects.SObjectType;
import org.bimserver.interfaces.objects.SProgressTopicType;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.ifc2x3tc1.IfcBuildingStorey;
import org.bimserver.models.ifc2x3tc1.IfcLocalPlacement;
import org.bimserver.models.ifc2x3tc1.IfcObjectDefinition;
import org.bimserver.models.ifc2x3tc1.IfcObjectPlacement;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.models.ifc2x3tc1.IfcRelContainedInSpatialStructure;
import org.bimserver.models.ifc2x3tc1.IfcRelDecomposes;
import org.bimserver.models.ifc2x3tc1.IfcRelVoidsElement;
import org.bimserver.models.ifc2x3tc1.IfcSpatialStructureElement;
import org.bimserver.models.ifc2x3tc1.impl.IfcDistributionFlowElementImpl;
import org.bimserver.models.ifc2x3tc1.impl.IfcFlowTerminalImpl;
import org.bimserver.models.ifc2x3tc1.impl.IfcFurnishingElementImpl;
import org.bimserver.models.ifc2x3tc1.impl.IfcOpeningElementImpl;
import org.bimserver.models.ifc2x3tc1.impl.IfcVirtualElementImpl;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.models.store.ObjectDefinition;
import org.bimserver.models.store.ServiceDescriptor;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.models.store.Trigger;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.PluginException;
import org.bimserver.plugins.PluginManager;
import org.bimserver.plugins.services.BimServerClientException;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.plugins.services.NewRevisionHandler;
import org.bimserver.plugins.services.ServicePlugin;
import org.bimserver.shared.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.base.Charsets;

public class BuildingStoreyCheckServicePlugin extends ServicePlugin
{

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildingStoreyCheckServicePlugin.class);
	private boolean initialized;
	private static final String NAMESPACE = "http://bimserver.org/buildingStoreyCheck";
	@Override
	public void init(final PluginManager pluginManager) throws PluginException {
		super.init(pluginManager);
		initialized = true;
	}

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
		
		registerNewRevisionHandler(uoid, buildingStoreyCheck, new NewRevisionHandler() {
			public void newRevision(BimServerClientInterface bimServerClientInterface, long poid, long roid, String userToken, long soid, SObjectType settings) throws ServerException, UserException {
				
				try {
					
					LOGGER.info("BuildingStory is called");
					
					Date startDate = new Date(); 
					Long topicId = bimServerClientInterface.getRegistry().registerProgressOnRevisionTopic(SProgressTopicType.RUNNING_SERVICE, poid, roid, "Running Storey Checker");
					SLongActionState state = new SLongActionState();
					state.setTitle("BuildingStorey checker");
					state.setState(SActionState.STARTED);
					state.setProgress(-1);
					state.setStart(startDate);
					bimServerClientInterface.getRegistry().updateProgressTopic(topicId, state);
					
					SProject project;
					project = bimServerClientInterface.getBimsie1ServiceInterface().getProjectByPoid(poid);
				
					IfcModelInterface model =  bimServerClientInterface.getModel(project, roid, true, true);
					StringBuffer info = new StringBuffer();
					
					BiMap<Long, ? extends IdEObject> objects = model.getObjects();

					LOGGER.debug("# objects : " + objects.size());
					
					List<IfcBuildingStorey> storeyList = model.getAll(IfcBuildingStorey.class);
					int progress = 25;
					List<IfcProduct> floorRelatedProducts = new ArrayList<IfcProduct>();
					//First get all the directly connected products and remove them from the list of objects 
					int progressStep = 50/storeyList.size();
					for (IfcBuildingStorey ifcBuildingStorey : storeyList)
					{
						LOGGER.debug("processing storey : "+ ifcBuildingStorey.getName() + "(" + ifcBuildingStorey.getOid() + ")");	
						IfcSpatialStructureElement ifcSpatialStructureElement = (IfcSpatialStructureElement) ifcBuildingStorey;
						
						for (IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure : ifcSpatialStructureElement.getContainsElements())
						{ 
							LOGGER.debug("--found ifcRelContainedInSpatialStructure: " + ifcRelContainedInSpatialStructure.getOid() + " : " + ifcRelContainedInSpatialStructure.getName());
							objects.remove(ifcRelContainedInSpatialStructure.getOid());
							// get the related elements from the floors 
							for (IfcProduct ifcProduct : ifcRelContainedInSpatialStructure.getRelatedElements()) 
							{
								LOGGER.debug("--found product: " + ifcProduct.getOid() + " : " + ifcProduct.getName());
								floorRelatedProducts.add(ifcProduct);
							    LOGGER.debug("# remaining objects : " + objects.size());
							}
						    LOGGER.debug("# remaining objects : " + objects.size());
						}

						for (IfcRelDecomposes ifcRelDecomposes : ifcBuildingStorey.getIsDecomposedBy())
						{ 
							for (IfcObjectDefinition ifcObjectDefinition : ifcRelDecomposes.getRelatedObjects())
							{
								LOGGER.debug("--found product: " + ifcObjectDefinition.getOid() + " : " + ifcObjectDefinition.getName());
								floorRelatedProducts.add((IfcProduct) model.get(ifcObjectDefinition.getOid()));
							}
						}
						floorRelatedProducts.add(ifcBuildingStorey);
						progress += progressStep;
						state.setProgress(progress);
						
					}
					
					//remove found products from the list
					for (IfcProduct p : floorRelatedProducts)
						objects.remove(p.getOid());

					for (Long  eObjectId : objects.keySet())
					{
						if (model.get(eObjectId) instanceof IfcProduct)
						{   
							IfcProduct product = (IfcProduct)model.get(eObjectId);
							LOGGER.debug("--found product: " + product.getOid() + " : " + product.getName() + "(" + product.getClass() +")");							
							//IF it is furniture
							if (product instanceof IfcFurnishingElementImpl)
							{
								LOGGER.debug("----- furniture product");
								for (IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure : ((IfcFurnishingElementImpl)product).getContainedInStructure())
								{
									if (floorRelatedProducts.contains(ifcRelContainedInSpatialStructure.getRelatingStructure()))
									{
										LOGGER.debug("----- placed in: " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										floorRelatedProducts.add(product);
									}
									else
									{
										LOGGER.debug("----- should be placed : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										
									}
								}
							} 
							//IF its an Opening 
							if (product instanceof IfcOpeningElementImpl)
							{
								LOGGER.debug("----- opening product");
								IfcRelVoidsElement ifcRelVoidsElement =  ((IfcOpeningElementImpl)product).getVoidsElements();
								{
									if (floorRelatedProducts.contains(ifcRelVoidsElement.getRelatingBuildingElement()))
									{
										LOGGER.debug("----- placed in: " + ifcRelVoidsElement.getRelatingBuildingElement().getOid() + " : " + ifcRelVoidsElement.getRelatingBuildingElement().getName());
										floorRelatedProducts.add(product);
									}
									else
									{
										LOGGER.debug("----- should be placed : " + ifcRelVoidsElement.getRelatingBuildingElement().getOid() + " : " + ifcRelVoidsElement.getRelatingBuildingElement().getName());
										
									}
								}
							} 
							//IF its an IfcFlowTerminalImpl 
							if (product instanceof IfcFlowTerminalImpl)
							{
								LOGGER.debug("----- IfcFlowTerminalImpl product");
								for (IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure : ((IfcFlowTerminalImpl)product).getContainedInStructure())
								{
									if (floorRelatedProducts.contains(ifcRelContainedInSpatialStructure.getRelatingStructure()))
									{
										LOGGER.debug("----- placed in: " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										floorRelatedProducts.add(product);
									}
									else
									{
										LOGGER.debug("----- should be placed : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										
									}
								}
							} 
							//IfcVirtualElementImpl
							if (product instanceof IfcVirtualElementImpl)
							{ 
								LOGGER.debug("----- IfcVirtualElementImpl product");
								for (IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure : ((IfcVirtualElementImpl)product).getContainedInStructure())
								{
									if (floorRelatedProducts.contains(ifcRelContainedInSpatialStructure.getRelatingStructure()))
									{
										LOGGER.debug("----- placed in: " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										floorRelatedProducts.add(product);
									}
									else
									{
										LOGGER.debug("----- should be placed : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										
									}
								}
								if (product.getObjectPlacement() != null)
								{
									IfcObjectPlacement op = product.getObjectPlacement();
									for (IfcLocalPlacement lp : op.getReferencedByPlacements())
									{
										for (IfcProduct po : lp.getPlacesObject())
										{
											if (floorRelatedProducts.contains(po))
											{
												LOGGER.debug("----- placed in: " + po.getOid() + " : " + po.getName());
												floorRelatedProducts.add(product);
											}
										}
										
									}
								}
							} 
							//IfcDistributionFlowElementImpl
							if (product instanceof IfcDistributionFlowElementImpl)
							{
								LOGGER.debug("----- opening product");
								for (IfcRelContainedInSpatialStructure ifcRelContainedInSpatialStructure : ((IfcDistributionFlowElementImpl)product).getContainedInStructure())
								{
									if (floorRelatedProducts.contains(ifcRelContainedInSpatialStructure.getRelatingStructure()))
									{
										LOGGER.debug("----- placed in: " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										floorRelatedProducts.add(product);
									}
									else
									{
										LOGGER.debug("----- should be placed : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getOid() + " : " + ifcRelContainedInSpatialStructure.getRelatingStructure().getName());
										
									}
								}
							} 
						}
					}

					// Now that we have a list of floor related products , let;s check what is left 
					for (IfcProduct p : floorRelatedProducts)
						objects.remove(p.getOid());
					
					LOGGER.debug("-----------------------------------------" );
					LOGGER.debug("left over objects not related to a storey yet: " );
					for (Long key : objects.keySet())
					{
						if (model.get(key) instanceof IfcProduct)
						{   
							IfcProduct p = (IfcProduct) model.get(key);
							 LOGGER.debug("Orphan product: " + p.getOid() + " : " + p.getName() + "(" + p.getClass() +")");
							 info.append("Orphan product: " + p.getOid() + " : " + p.getName() + "(" + p.getClass() +")\n");
						}  							
					}	
					// We decomposed the stories, now we decompose each object and check the related objects, recursive within objects
					// for now with for loops, later in recursive methods. 
					
					//Write the result to log data of this run  
					List<java.lang.String> infos = new ArrayList<String>();
					infos.add(info.toString());
					
					state = new SLongActionState();
					state.setProgress(100);
					state.setInfos(infos);
					state.setTitle("BuildingStorey checker");
					state.setState(SActionState.FINISHED);
					state.setStart(startDate);
					state.setEnd(new Date());
					bimServerClientInterface.getRegistry().updateProgressTopic(topicId, state);
					bimServerClientInterface.getRegistry().unregisterProgressTopic(topicId);
				    addExtendedData(info.toString().getBytes(Charsets.UTF_8), "BuildingStoreycheckerlog.txt", "BuildingStoreycheckerlog", "text", bimServerClientInterface, roid);
				} catch (BimServerClientException
						| PublicInterfaceNotFoundException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void addExtendedData(byte[] data, String filename, String title, String mime, BimServerClientInterface bimServerClientInterface, long roid) {
		try {
			
			SExtendedDataSchema extendedDataSchemaByNamespace = bimServerClientInterface.getBimsie1ServiceInterface().getExtendedDataSchemaByNamespace(
					NAMESPACE);

			SFile file = new SFile();
			SimpleDateFormat sdf = new SimpleDateFormat("YYYY-dd-MM HH:mm");
			SExtendedData extendedData = new SExtendedData();
			extendedData.setTitle("BuildingStoreyCheck Results(" + sdf.format(new Date()) + ")");
			file.setFilename("buildingStoreyCheck.txt");
			extendedData.setSchemaId(extendedDataSchemaByNamespace.getOid());
			try {
				file.setData(data);
				file.setMime(mime);

				long fileId = bimServerClientInterface.getServiceInterface().uploadFile(file);
				extendedData.setFileId(fileId);

				bimServerClientInterface.getBimsie1ServiceInterface().addExtendedDataToRevision(roid, extendedData);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}

	@Override
	public String getDescription() {
		return "Provides building storey check service";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public String getTitle() {
		return "BuildingStoreyCheck";
	}

	@Override
	public String getDefaultName() {
		return "BuildingStoreyCheck";
	}

	@Override
	public void unregister(SInternalServicePluginConfiguration internalService) {
	}

	@Override
	public ObjectDefinition getSettingsDefinition() {
		return null;
	}
	
	
}