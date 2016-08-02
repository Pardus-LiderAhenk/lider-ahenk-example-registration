package tr.org.liderahenk.registration.subscriber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tr.org.liderahenk.lider.core.api.configuration.IConfigurationService;
import tr.org.liderahenk.lider.core.api.ldap.ILDAPService;
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
import tr.org.liderahenk.lider.core.model.ldap.LdapEntry;

public class RegistrationSubscriberImpl implements IRegistrationSubscriber, IScriptResultSubscriber {

	private static Logger logger = LoggerFactory.getLogger(RegistrationSubscriberImpl.class);

	private ILDAPService ldapService;
	private IConfigurationService configurationService;
	private IAgentDao agentDao;
	private IEntityFactory entityFactory;

	public ILiderMessage messageReceived(IRegistrationMessage message) throws Exception {

		String jid = message.getFrom().split("@")[0];

		if (AgentMessageType.REGISTER == message.getType()) {

//			csv de mac,ou1,ou2,... 
			
			boolean alreadyExists = false;
			String dn = null;
			String cn=null;

			// Try to find agent LDAP entry
			final List<LdapEntry> entries = ldapService.search(configurationService.getAgentLdapJidAttribute(), jid,new String[] { configurationService.getAgentLdapJidAttribute() });
			LdapEntry entry = entries != null && !entries.isEmpty() ? entries.get(0) : null;
			
//			entry=null;
					
			if (entry != null) {
				alreadyExists = true;
				dn = entry.getDistinguishedName();
				logger.info("Updating LDAP entry: {} with password: {}",new Object[] { message.getFrom(), message.getPassword() });
				// Update agent LDAP entry.
				ldapService.updateEntry(dn, "userPassword", message.getPassword());
				logger.info("Agent LDAP entry {} updated successfully!", dn);
			} else {
				
				logger.info("1");
				CsvReader a=new CsvReader();
				logger.info("2");
				Map<String, String[]> expectedRecordsMap=a.read("/home/volkan/git/lider-ahenk-example-registration/src/main/java/tr/org/liderahenk/records.csv");
				
				
				logger.info("mac address: {}", message.getMacAddresses());
				
				String [] macAddresses = message.getMacAddresses().split(",");
				
				logger.info("3");
				String[] dcParameters=null;
				
				logger.info("4");
				if(macAddresses.length>0){
					logger.info("5");
					for (String macAddress : macAddresses) {
						
						logger.info("5.5 mac:{}",macAddress);
						dcParameters=expectedRecordsMap.get(macAddress.replace("'", ""));
						
						logger.info("6");
						if(dcParameters!=null){
							logger.info("7");
							cn=macAddress.replace("'", "");
							logger.info("8 cn={}",cn);
							break;
						}
					}
					
				}
				else{
					//default registration
				}
			
				dn = createEntryDN(cn,dcParameters);
				
				logger.info("Creating LDAP dn: {} with password: {}", new Object[] { dn, message.getPassword() });
				ldapService.addEntry(dn, computeAttributes(cn, message.getPassword(),dcParameters));
				logger.info("Agent LDAP entry {} created successfully!", dn);
			}

			
			
//			
//			
//			// Try to find related agent database record
//			List<? extends IAgent> agents = agentDao.findByProperty(IAgent.class, "jid", jid, 1);
//			IAgent agent = agents != null && !agents.isEmpty() ? agents.get(0) : null;
//
//			if (agent != null) {
//				alreadyExists = true;
//				// Update the record
//				agent = entityFactory.createAgent(agent, message.getPassword(), message.getHostname(),
//						message.getIpAddresses(), message.getMacAddresses(), message.getData());
//				agentDao.update(agent);
//			} else {
//				// Create new agent database record
//				agent = entityFactory.createAgent(null, jid, dn, message.getPassword(), message.getHostname(),
//						message.getIpAddresses(), message.getMacAddresses(), message.getData());
//				agentDao.save(agent);
//			}
//
//			if (alreadyExists) {
//				logger.warn(
//						"Agent {} already exists! Updated its password and database properties with the values submitted.",
//						dn);
//				return new RegistrationResponseMessageImpl(StatusCode.ALREADY_EXISTS,
//						dn + " already exists! Updated its password and database properties with the values submitted.",
//						dn, null, new Date());
//			} else {
//				logger.info("Agent {} and its related database record created successfully!", dn);
//				return new RegistrationResponseMessageImpl(StatusCode.REGISTERED,
//						dn + " and its related database record created successfully!", dn, null, new Date());
//			}
			
			
			
			// TODO
			// TODO
			// TODO
			logger.info("Registration triggered");

		} else if (AgentMessageType.UNREGISTER == message.getType()) {
			// Check if agent LDAP entry already exists
			final List<LdapEntry> entry = ldapService.search(configurationService.getAgentLdapJidAttribute(), jid,
					new String[] { configurationService.getAgentLdapJidAttribute() });

			// Delete agent LDAP entry
			if (entry != null && !entry.isEmpty()) {
				ldapService.deleteEntry(entry.get(0).getDistinguishedName());
			}

			// Find related agent database record.
			List<? extends IAgent> agents = agentDao.findByProperty(IAgent.class, "jid", jid, 1);
			IAgent agent = agents != null && !agents.isEmpty() ? agents.get(0) : null;

			// Mark the record as deleted.
			if (agent != null) {
				agentDao.delete(agent.getId());
			}

			return null;
		}

		return null;
	}

	private Map<String, String[]> computeAttributes(final String cn, final String password, String[] dcParameters) {
		Map<String, String[]> attributes = new HashMap<String, String[]>();
		attributes.put("objectClass", configurationService.getAgentLdapObjectClasses().split(","));
		attributes.put(configurationService.getAgentLdapIdAttribute(), new String[] { cn });
		attributes.put(configurationService.getAgentLdapJidAttribute(), new String[] { cn });
		attributes.put("userPassword", new String[] { password });
		// FIXME remove this line, after correcting LDAP schema!
		
		ArrayList<String> list = new ArrayList<String>();
		list.add("ou=Uncategorized");
		
		for (String dcParam : dcParameters) {
			list.add(",dc="+dcParam);
		}
		
		
		attributes.put("owner", list.toArray(new String[list.size()]));
		return attributes;
	}
	
	private String createEntryDN(String cn, String[] dcParameters) {
		StringBuilder entryDN = new StringBuilder();
		// Generate agent ID attribute
		entryDN.append(configurationService.getAgentLdapIdAttribute());
		entryDN.append("=");
		entryDN.append(cn);
		// Append base DN
		for (String dcValue : dcParameters) {
			entryDN.append(",dc="+dcValue);
		}
		return entryDN.toString();
	}
	
	
	public ILiderMessage postRegistration() throws Exception {

		
//		execute string message
		// TODO
		// TODO
		// TODO
		logger.info("Post-registration triggered");

		return null;
	}

	public void messageReceived(IScriptResultMessage message) throws Exception {

//		execute string result -> extend reg attr
		// TODO
		// TODO
		// TODO

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

}
