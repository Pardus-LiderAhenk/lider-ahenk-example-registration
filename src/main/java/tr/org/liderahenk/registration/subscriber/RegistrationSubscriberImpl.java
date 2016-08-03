package tr.org.liderahenk.registration.subscriber;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tr.org.liderahenk.lider.core.api.configuration.IConfigurationService;
import tr.org.liderahenk.lider.core.api.exceptions.LdapException;
import tr.org.liderahenk.lider.core.api.ldap.ILDAPService;
import tr.org.liderahenk.lider.core.api.messaging.IMessageFactory;
import tr.org.liderahenk.lider.core.api.messaging.enums.AgentMessageType;
import tr.org.liderahenk.lider.core.api.messaging.enums.StatusCode;
import tr.org.liderahenk.lider.core.api.messaging.messages.ILiderMessage;
import tr.org.liderahenk.lider.core.api.messaging.messages.IRegistrationMessage;
import tr.org.liderahenk.lider.core.api.messaging.messages.IScriptResultMessage;
import tr.org.liderahenk.lider.core.api.messaging.subscribers.IRegistrationSubscriber;
import tr.org.liderahenk.lider.core.api.messaging.subscribers.IScriptResultSubscriber;
import tr.org.liderahenk.lider.core.api.persistence.dao.IAgentDao;
import tr.org.liderahenk.lider.core.api.persistence.entities.IAgent;
import tr.org.liderahenk.lider.core.api.persistence.factories.IEntityFactory;
import tr.org.liderahenk.lider.core.api.utils.FileCopyUtils;
import tr.org.liderahenk.lider.core.model.ldap.LdapEntry;

public class RegistrationSubscriberImpl implements IRegistrationSubscriber, IScriptResultSubscriber {

	private static Logger logger = LoggerFactory.getLogger(RegistrationSubscriberImpl.class);

	private ILDAPService ldapService;
	private IConfigurationService configurationService;
	private IAgentDao agentDao;
	private IEntityFactory entityFactory;
	private IMessageFactory messageFactory;
	

	public String rootDc="dc=mys,dc=pardus,dc=org";
	public String fullJid;
	
	
	public ILiderMessage messageReceived(IRegistrationMessage message) throws Exception {

		InputStream inputStream=null;
		inputStream=getClass().getResourceAsStream("/records.csv") ;

		fullJid = message.getFrom();
		
		
		if (AgentMessageType.REGISTER == message.getType()) {

			boolean alreadyExists = false;
			Entry definedEntry=getEnryFromCsvFile(message.getMacAddresses(),inputStream);

			if (definedEntry != null){//example registration
				
				String dn = null;
				
				//is registered in ldap?
				final List<LdapEntry> entries = ldapService.search("cn", definedEntry.ouParameters[0],new String[] { "cn" });
				LdapEntry entry = entries != null && !entries.isEmpty() ? entries.get(0) : null;
				
				
				//already registered
				if (entry != null){
					alreadyExists = true;
					dn = entry.getDistinguishedName();
					logger.info("Updating LDAP entry: {} with password: {}",new Object[] { message.getFrom(), message.getPassword() });
					// Update agent LDAP entry.
					ldapService.updateEntry(dn, "userPassword", message.getPassword());
					logger.info("Agent LDAP entry {} updated successfully!", dn);
				}
				
				//not registered yet
				else{
					
					//create dn and check is ou level created. if does not exist, create!
					dn = createEntryDN(definedEntry.cn,definedEntry.ouParameters);
					ldapService.addEntry(dn, computeAttributes(definedEntry.cn, message.getPassword(),definedEntry.ouParameters));
				}
				

				// Try to find related agent database record
				List<? extends IAgent> agents = agentDao.findByProperty(IAgent.class, "jid", definedEntry.ouParameters[0], 1);
				IAgent agent = agents != null && !agents.isEmpty() ? agents.get(0) : null;

				if (agent != null) {
					alreadyExists = true;
					// Update the record
					agent = entityFactory.createAgent(agent, message.getPassword(), message.getHostname(),message.getIpAddresses(), message.getMacAddresses(), message.getData());
					agentDao.update(agent);
				} else {
					// Create new agent database record
					agent = entityFactory.createAgent(null, definedEntry.ouParameters[0], dn, message.getPassword(), message.getHostname(),message.getIpAddresses(), message.getMacAddresses(), message.getData());
					agentDao.save(agent);
				}

				if (alreadyExists) {
					logger.warn("Agent {} already exists! Updated its password and database properties with the values submitted.",dn);
					return messageFactory.createRegistrationResponseMessage(null, StatusCode.ALREADY_EXISTS, dn + " already exists! Updated its password and database properties with the values submitted.", dn);
				} else {
					logger.info("Agent {} and its related database record created successfully!", dn);
					return messageFactory.createRegistrationResponseMessage(null, StatusCode.REGISTERED, dn + " and its related database record created successfully!", dn);
				}
				
				
			}
			else{//default registration
				//TODO
				
			}
			
			// TODO
			// TODO
			// TODO
			logger.info("Registration triggered");

		} else if (AgentMessageType.UNREGISTER == message.getType()) {
			//TODO
//			// Check if agent LDAP entry already exists
//			final List<LdapEntry> entry = ldapService.search(configurationService.getAgentLdapJidAttribute(), jid,
//					new String[] { configurationService.getAgentLdapJidAttribute() });
//
//			// Delete agent LDAP entry
//			if (entry != null && !entry.isEmpty()) {
//				ldapService.deleteEntry(entry.get(0).getDistinguishedName());
//			}
//
//			// Find related agent database record.
//			List<? extends IAgent> agents = agentDao.findByProperty(IAgent.class, "jid", jid, 1);
//			IAgent agent = agents != null && !agents.isEmpty() ? agents.get(0) : null;
//
//			// Mark the record as deleted.
//			if (agent != null) {
//				agentDao.delete(agent.getId());
//			}

			return null;
		}

		return null;
	}

	
	
	public ILiderMessage postRegistration() throws Exception {
		String command="cat /etc/system.properties";
		
		logger.info("Post-registration triggered");
		logger.info("Execute script message is sending. Command :{}",command);

		return messageFactory.createExecuteScriptMessage(fullJid, command, configurationService.getFileServerConf(fullJid.split("@")[0]));
	}

	public void messageReceived(IScriptResultMessage message) throws Exception {

		logger.info("Execute script message result handling.");
		
		if(message.getResultCode()!=null && message.getResultCode()>0){
			logger.error("Script execution failed. Result code: {}",message.getResultCode());
		}
		else{
			logger.info("Script executed successfully.");
			Map<String, Object> propertiesMap = new HashMap<String, Object>();
			propertiesMap.put("jid", message.getFrom().split("@")[0]);
			
			List<? extends IAgent> agents = agentDao.findByProperties(IAgent.class, propertiesMap, null, 1);
			
			if (agents!=null && !agents.isEmpty()){
				IAgent agent=agents.get(0);
				
				Map<String, Object> newPropertiesMap = new HashMap<String, Object>();
				
				logger.info("Fetching script result.");
				
				String filePath = configurationService.getFileServerAgentFilePath().replaceFirst("\\{0\\}", message.getFrom().split("@")[0]);
				if (!filePath.endsWith("/"))
				    filePath += "/";
				filePath += message.getMd5().toString();

				logger.info("Filepath:{}",filePath);
				
				byte[] data = new FileCopyUtils().copyFile(configurationService.getFileServerHost(),
				    configurationService.getFileServerPort(), configurationService.getFileServerUsername(),
				    configurationService.getFileServerPassword(), filePath, "/tmp/lider");
				
				logger.info("New property adding to agent properties {}",new String(data, StandardCharsets.UTF_8));
				String properties=new String(data, StandardCharsets.UTF_8);
				String[] propertiesArr= properties.split(",");
				
				
				logger.info("Parsing properties");
				
				if (propertiesArr!=null && propertiesArr.length>0){
					for (String prop : propertiesArr) {
						if (prop.split(":").length>1){
							newPropertiesMap.put(prop.split(":")[0].replace("\n", ""), prop.split(":")[1].replace("\n", ""));
						}
					}
				}
				
				entityFactory.createAgent(agent, agent.getPassword(), agent.getHostname(),agent.getIpAddresses(),agent.getMacAddresses(), newPropertiesMap);
				agentDao.update(agent);
				logger.info("Agent updated with new properties");
				
			}
			else{
				logger.error("Jid not found:{}",message.getFrom().split("@")[0]);
			}
		}

	}
	
	
	private boolean organizationUnitDoesExist(String ouValue) throws LdapException {

		final List<LdapEntry> enryList = ldapService.search("ou", ouValue,new String[] { "ou" });
		
		if (enryList !=null && enryList.size()>0){
			return true;
		}
		else{
			return false;
		}
	}


	private Map<String, String[]> computeAttributes(final String cn, final String password, String[] dcParameters) {
		
		Map<String, String[]> attributes = new HashMap<String, String[]>();
		attributes.put("objectClass", new String[] { "device","pardusDevice" });
		attributes.put("cn", new String[] { cn });
		attributes.put("uid", new String[] { fullJid.split("@")[0] });
		attributes.put("userPassword", new String[] { password });
		attributes.put("owner", new String[] { "ou=Uncategorized,dc=mys,dc=pardus,dc=org" });
		return attributes;
	}
	
	private String createEntryDN(String cn, String[] ouParameters) throws LdapException {
		String incrementaldn=rootDc;
		
		logger.error("incrementaldn"+incrementaldn);
		
		for (String ouValue : ouParameters) {
			incrementaldn="ou="+ouValue+","+incrementaldn;
			logger.error("incrementaldn"+incrementaldn);
			
			if(organizationUnitDoesExist(ouValue)==false){
				logger.error(ouValue+" yok ekleyecez");
				Map<String, String[]> ouMap= new HashMap<String, String[]>();
				ouMap.put("objectClass", new String[]{"top","organizationalUnit"});
				ouMap.put("ou", new String[]{ouValue});
				ldapService.addEntry(incrementaldn, ouMap);
			}
		}
		
		incrementaldn="cn="+cn+","+incrementaldn;
		return incrementaldn;
	}
	
	
	
	public class Entry {
	    String cn;
	    String[] ouParameters;
	    public Entry(String cn,String[] ouParameters){
	    	this.cn=cn;
	    	this.ouParameters=ouParameters;
	    }
	}
	
	public Entry getEnryFromCsvFile(String strMacAddresses,InputStream inputStream){
		
		CsvReader csvReader=new CsvReader();
		Map<String, String[]> expectedRecordsMap=csvReader.read(inputStream);
		
		String [] macAddresses = strMacAddresses.split(",");
		String[] ouParameters=null;
		String cn=null;
		
		if(macAddresses.length>0){
			for (String macAddress : macAddresses) {
				ouParameters=expectedRecordsMap.get(macAddress.replace("'", ""));
				if(ouParameters!=null && ouParameters.length>1){
					cn=ouParameters[0];
					break;
				}
			}
		}
		
		if (ouParameters==null || cn==null){
			return null;
		}
		return new Entry(cn,Arrays.copyOfRange(ouParameters, 1, ouParameters.length));
	}

	/**
	 * 
	 * @param ldapService
	 */
	public void setLdapService(ILDAPService ldapService) {
		this.ldapService = ldapService;
	}

	/**
	 * 
	 * @param configurationService
	 */
	public void setConfigurationService(IConfigurationService configurationService) {
		this.configurationService = configurationService;
	}

	/**
	 * 
	 * @param agentDao
	 */
	public void setAgentDao(IAgentDao agentDao) {
		this.agentDao = agentDao;
	}

	/**
	 * 
	 * @param entityFactory
	 */
	public void setEntityFactory(IEntityFactory entityFactory) {
		this.entityFactory = entityFactory;
	}
	
	/**
	 * 
	 * @param messageFactory
	 */
	public void setMessageFactory(IMessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

}
