package com.titlenine.integration.bronto;

import com.bronto.api.model.*;

import com.bronto.api.BrontoApi;
import com.bronto.api.BrontoClient;
import com.bronto.api.ObjectOperations;
import com.bronto.api.model.ContactObject;
import com.bronto.api.model.DeliveryObject;
import com.bronto.api.model.DeliveryRecipientObject;
import com.bronto.api.model.DeliveryRecipientSelection;
import com.bronto.api.model.DeliveryRecipientType;
import com.bronto.api.model.DeliveryType;
import com.bronto.api.model.MailListObject;
import com.bronto.api.model.MessageFieldObject;
import com.bronto.api.model.MessageObject;
import com.bronto.api.model.ObjectFactory;
import com.bronto.api.model.ResultItem;
import com.bronto.api.model.WriteResult;
import com.bronto.api.operation.ContactOperations;
import com.bronto.api.operation.MailListOperations;
import com.bronto.api.operation.MessageOperations;
import com.bronto.api.request.ContactReadRequest;
import com.bronto.api.request.MailListReadRequest;
import com.bronto.api.request.MessageReadRequest;
import com.titlenine.integration.cw.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.mutable.MutableBoolean;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CopyOfCwBrontoApiConnector {
	public CopyOfCwBrontoApiConnector(){
		createCarriers(carriers);
		createNonInvItems(nonInvItems);
		createUtilItems(utilItems);
		createMessageNames(messageNames);
		sdf.applyPattern("MMddyyyyHHmm");
		outputLog = sdf.format(new Date());		
	}

	static final String TOKEN = "22DF4038-A0F4-43B1-97AD-F593CF9D1C06";
    static final String WSDL = "https://api.bronto.com/v4?wsdl";
    static final String URL = "https://api.bronto.com/v4";
    
    static final String UPSURL = "https://www.ups.com/tracking/tracking.html";
    static final String UPSQP = "?track=yes&trackNums;=";
    static final String USPSURL = "https://tools.usps.com/go/TrackConfirmAction";
    static final String USPSQP = "?qtc_tLabels1=";
    static final String FEDEXURL = "https://www.fedex.com/apps/fedextrack/?action=track";
    static final String FEDEXQP = "&tracknumbers=";
    static final String NEWGURL = "http://tracking.smartlabel.com/";

	private LinkedHashMap<String, String> cmdArgs = new LinkedHashMap<String, String>(); 
    private LinkedHashMap<String, Carrier> carriers = new LinkedHashMap<String, Carrier>();
	private LinkedHashMap<String, OrderMessage> nonInvItems = new LinkedHashMap<String, OrderMessage>();
	private LinkedHashMap<String, String> utilItems = new LinkedHashMap<String, String>();
	private LinkedHashMap<String, String> messageNames = new LinkedHashMap<String, String>();
	private LinkedHashMap<String, String> thisMessage = new LinkedHashMap<String, String>();
	private Collection<File> emailFiles = new ArrayList<File>();
	private String filePath;
	private String outputLog; 
	private String debugLevel = "log"; // log = default, debug = verbose
	protected SimpleDateFormat sdf = new SimpleDateFormat();
	
	//* start main()
    public static void main(String[] args){ 
    	CopyOfCwBrontoApiConnector connector = new CopyOfCwBrontoApiConnector();
    	connector.log("************************************************************");
    	connector.log("Running CwBrontoApiConnector.java");
    	connector.log("************************************************************");
    	
    	connector.parseCmdArgs(args, connector.cmdArgs);	
		connector.debugLevel = connector.setDebugLevel();
		connector.log("Debug level: " + connector.debugLevel);
		connector.filePath = connector.setFiles();
    	
		int fileCount = 1;
    	for (File newFile : connector.emailFiles) {
    		connector.thisMessage = new LinkedHashMap();
    		connector.log("File " + "#" + fileCount++ + ": " + newFile.getName());
					
    		boolean sendIndicator = connector.parseXML(connector.thisMessage, newFile, 
					connector.carriers, connector.nonInvItems, connector.utilItems).getValue();
    		
			connector.sendEmail( sendIndicator, connector.thisMessage, connector.cmdArgs, newFile.getName());	    	
    	}

    	connector.log("************************************************************");
    	connector.log("CwBrontoApiConnector concluded");
    	connector.log("************************************************************");
    } // end of main()
    
    // METHODS
    
    //* parse command line arguments into key/value pairs
    public void parseCmdArgs(String[] args, LinkedHashMap cmdArgs) {
		if ( args != null && args.length > 0 ) {
			for (String arg : args) {
				if (arg.indexOf("=") > -1) {
					String[] pair = arg.split("=");
					cmdArgs.put(pair[0], pair[1]);
					debug("(command arg) " + pair[0] + " = " + pair[1]);
				}
			}
		} else {
			debug("No command args");
		}
	} // end of parseCmdArgs()
    
    //* sets debug level if present 
    public String setDebugLevel() {
    	if (cmdArgs.get("debugLevel") != null && cmdArgs.get("debugLevel") != "") {
			return (String)cmdArgs.get("debugLevel");
		}
    	return "log";
    } // end of setDebugLevel()
    
    //* populates emailFiles based on starting filepath
    public String setFiles() {
    	String startPoint;
		if (cmdArgs.get("filePath") != null && cmdArgs.get("filePath") != "") {
			startPoint = cmdArgs.get("filePath");
		} else {
			startPoint = "C:\\Users\\dgrossma\\Desktop\\SampleXMLMessages\\FRANKENFILE.xml";
		}
		log("Starting filepath: " + startPoint);
		addTree(new File(startPoint), emailFiles);
		return startPoint;
    } // end of setFilePath()
    
    //* recursively collect all files within "file"
    public void addTree(File file, Collection<File> emailFiles) {
    	if (file.isFile() && !file.isHidden() && file.getName().indexOf(".xml") > -1 ) {
			emailFiles.add(file);
		}
    	File[] children = file.listFiles();
    	if ( children != null ) {
    		for (File child : children) {
    			if ( !"Archive".equalsIgnoreCase(child.getName())) {
	    			if (child.isFile() && !child.isHidden() && child.getName().indexOf(".xml") > -1 ) {
	    				emailFiles.add(child);
	    			}
    			}
    		}
    	}
    	debug("Files in queue:");
    	for (File f : emailFiles) {
    		debug("(file): " + f.getAbsolutePath());
    	}
    } // end of addTree()

    //* take invoice items with no associated image and put them in a LinkedHashMap
    public static void createUtilItems(LinkedHashMap<String, String> utilItems) {
    	utilItems.put("MISC", "n");
    }
    
    //* populate a LinkedHashMap with pairs of notification_type/brontoMessageName
    //** Long term plan is to populate these hashmaps using external files for easier editing
    public static void createMessageNames(LinkedHashMap<String, String> messageNames) {
        messageNames.put("B1", "CwBackorderNotification_1");
        messageNames.put("B2", "CwBackorderNotification_2");
        messageNames.put("B3", "CwBackorderNotification_3");
        messageNames.put("RC", "CwReturnNotification_3");
        messageNames.put("SC", "CwShipConfirmation");
        messageNames.put("S1", "CwSoldOutNotification");
        messageNames.put("v01", "CwEgiftCardNotification_1");
        messageNames.put("v02", "CwEgiftCardNotification_2");
        messageNames.put("v03", "CwEgiftCardNotification_3");
        messageNames.put("v04", "CwEgiftCardNotification_4");
        messageNames.put("SV", "Fail");
    }
    
    //* take items that will come out of CW as invoice items but should not be treated as such and put them in a LinkedHashMap
    public void createNonInvItems(LinkedHashMap<String, OrderMessage> nonInvItems) {
    	nonInvItems.put("4300", new OrderMessage("Sorry for the delay with your original order. " + 
    			"Please ignore the \"order total.\" You were not charged for this replacement.<br/><br/>" +
    			"If your original package arrives in addition to this replacement, " + 
    			"please give us a call at 800.342.4448 so that we can help you return it at no cost to you. " + 
    			"If you decide to keep the original package, we'll charge you for the items.", "CS"));
    	nonInvItems.put("4400", new OrderMessage("Sorry for the delay with your original order. " + 
    			"Please ignore the \"order total.\" You were not charged for this replacement.<br/><br/>" +
    			"If your original package arrives in addition to this replacement, " + 
    			"please give us a call at 800.342.4448 so that we can help you return it at no cost to you. " + 
    			"If you decide to keep the original package, we'll charge you for the items.", "CS"));
    	nonInvItems.put("3000", new OrderMessage("Rats! We're sorry that you received a defective item. " + 
    			"You have not been charged for this replacement and you can ignore the \"Order Total\" on this invoice.<br/><br/>" +
    			"Please return the defective item to us within 30 days to avoid being charged for it. " + 
    			"Give us a call at 800.342.4448 so that we can help you return it at no cost to you.", "CS"));
    	nonInvItems.put("3100", new OrderMessage("We goofed! Apologies for sending you the wrong item. " + 
    			"You have not been charged for this replacement and you can ignore the \"Order Total\" on this invoice.<br/><br/>" +
    			"Please return the incorrect item to us within 30 days to avoid being charged for it. " + 
    			"Give us a call at 800.342.4448 so that we can help you return it at no cost to you.", "CS"));
    	nonInvItems.put("1500", new OrderMessage("We�re sorry that your package was missing items! " + 
    			"Please ignore the \"Order Total\" on this invoice. You were not charged for this replacement!", "CS"));
    	nonInvItems.put("4500", new OrderMessage("We goofed! Apologies for sending you the wrong item. " + 
    			"You have not been charged for this replacement and you can ignore the \"Order Total\" on this invoice.<br/><br/>" +
    			"Please return the incorrect item to us within 30 days to avoid being charged for it." +
    			"Give us a call at 800.342.4448 so that we can help you return it at no cost to you.", "CS"));
    	nonInvItems.put("3200", new OrderMessage("We goofed! Apologies for sending you the wrong item. " + 
    			"You have not been charged for this replacement and you can ignore the \"Order Total\" on this invoice.<br/><br/>" +
    			"Please return the incorrect item to us within 30 days to avoid being charged for it." +
    			"Give us a call at 800.342.4448 so that we can help you return it at no cost to you.", "CS"));
    	nonInvItems.put("2200", new OrderMessage("Thank you for your exchange order!", "CS"));
    	nonInvItems.put("2100", new OrderMessage("Thank you for your exchange order!", "CS"));
    	nonInvItems.put("CATLG", new OrderMessage("", "X"));
    	nonInvItems.put("GFTWRP", new OrderMessage("", "X"));
    	nonInvItems.put("GFTCRD", new OrderMessage("", "X"));
    	nonInvItems.put("CROONR", new OrderMessage("", "X"));
    	nonInvItems.put("GIFT5", new OrderMessage("", "X"));
    } // end of createNonInvItems()
    
    //* put carrier data in a LinkedHashMap
    public static void createCarriers(LinkedHashMap<String, Carrier> carriers) {
    	carriers.put("1", new Carrier("1", "", "", "BEST WAY - GROUND"));    	
    	carriers.put("2", new Carrier("2", "", "", "EXPRESS"));    	
    	carriers.put("3", new Carrier("3", "", "", "ZAP"));
    	carriers.put("4", new Carrier("4", "", "", "STANDARD SHIPPING TO AK/HI"));    	
    	carriers.put("9", new Carrier("9", "", "", "LICKETY-SPLIT"));    	
    	carriers.put("10", new Carrier("10", "", "", "*DON'T USE* LICKETY-SPLITAK/HI"));    	
    	carriers.put("13", new Carrier("13", "USPS", USPSURL, "FIRST CLASS ENVELOPE", USPSQP)); 	
    	carriers.put("16", new Carrier("16", "USPS", USPSURL, "FIRST CLASS MAIL", USPSQP));	
    	carriers.put("17", new Carrier("17", "USPS", USPSURL, "PRIORITY MAIL - USPS", USPSQP));	
    	carriers.put("18", new Carrier("18", "USPS", USPSURL, "EXPRESS MAIL - USPS", USPSQP));	
    	carriers.put("19", new Carrier("19", "USPS", USPSURL, "PRIORITY INSURED - USPS", USPSQP));
    	carriers.put("20", new Carrier("20", "FedEx", FEDEXURL, "FEDEX EXPRESS SAVER - 3 DAY", FEDEXQP));   	
    	carriers.put("21", new Carrier("21", "UPS", UPSURL, "UPS MAIL INNOVATIONS", UPSQP));
    	carriers.put("33", new Carrier("33", "USPS", USPSURL, "APO SHIPPING", USPSQP));    	
    	carriers.put("40", new Carrier("40", "FedEx", FEDEXURL, "FEDEX GROUND", FEDEXQP));    	
    	carriers.put("41", new Carrier("41", "FedEx", FEDEXURL, "FEDEX PRIORITY OVERNIGHT", FEDEXQP));
    	carriers.put("42", new Carrier("42", "FedEx", FEDEXURL, "FEDEX STANDARD OVERNIGHT", FEDEXQP));    	
    	carriers.put("43", new Carrier("43", "FedEx", FEDEXURL, "FEDEX 2 DAY", FEDEXQP));    	
    	carriers.put("44", new Carrier("44", "FedEx", FEDEXURL, "FEDEX 2ND DAY AIR - AK/HI", FEDEXQP));    	
    	carriers.put("50", new Carrier("50", "UPS", UPSURL, "UPS GROUND", UPSQP));    	
    	carriers.put("51", new Carrier("51", "UPS", UPSURL, "UPS 2ND DAY AIR", UPSQP));    	
    	carriers.put("52", new Carrier("52", "UPS", UPSURL, "UPS NEXT DAY AIR SAVER", UPSQP));    	
    	carriers.put("53", new Carrier("53", "UPS", UPSURL, "UPS NEXT DAY AIR", UPSQP));    	
    	carriers.put("54", new Carrier("54", "UPS", UPSURL, "UPS 3 DAY SELECT", UPSQP));    	
    	carriers.put("55", new Carrier("55", "UPS", UPSURL, "UPS BASIC", UPSQP));    	
    	carriers.put("56", new Carrier("56", "UPS", UPSURL, "UPS 2ND DAY AIR - AK/HI", UPSQP));   	
    	carriers.put("66", new Carrier("66", "Newgistics", NEWGURL, "NGS PARCEL SELECT"));    	
    	carriers.put("67", new Carrier("67", "Newgistics", NEWGURL, "NGS STANDARD MAIL MACHINABLE"));    	
    	carriers.put("74", new Carrier("74", "USPS", USPSURL, "AIRMAIL PARCEL POST - USPS", USPSQP));    	
    	carriers.put("76", new Carrier("76", "USPS", USPSURL, "GLOBAL EXPRESS MAIL - USPS", USPSQP));    	
    	carriers.put("77", new Carrier("77", "USPS", USPSURL, "USPS INTERNATIONAL", USPSQP));    	
    	carriers.put("78", new Carrier("78", "USPS", USPSURL, "CANADIAN GLOBAL EXPRESS", USPSQP));    	
    	carriers.put("82", new Carrier("82", "USPS", USPSURL, "MAIL INNOVATIONS EXPEDITE PSLW", USPSQP));    	
    	carriers.put("83", new Carrier("83", "USPS", USPSURL, "MAIL INNOVATIONS EXPEDITED PL", USPSQP));    	
    	carriers.put("92", new Carrier("92", "FedEx", FEDEXURL, "FEDEX GROUND COMMERCIAL", FEDEXQP));    	
    	carriers.put("95", new Carrier("95", "FedEx", FEDEXURL, "FEDEX SMARTPOST", FEDEXQP));    	
    	carriers.put("97", new Carrier("97", "", "", "T9 TRUCK"));    	
    	carriers.put("98", new Carrier("98", "", "", "EXPRESS BILL"));    	
    	carriers.put("99", new Carrier("99", "", "", "EMPLOYEE PURCHASE"));
    } // end of createCarriers()
    
    //* sends email if appropriate, collects info for database
    public void sendEmail (boolean isSendable, LinkedHashMap<String, String> thisMessage, 
    		LinkedHashMap<String, String> cmdArgs, String fileName) {
    	if(isSendable) {
    		log("Preparing to send");
    	} else {
    		log("Preparing to log without sending");
    	}
    	
    	String sendStatus = "";
    	String apiResponse = "";
    	String tempEmail = "";

    	BrontoApi client = new BrontoClient(TOKEN);
        String sessionId = client.login();	
        ContactOperations contactOps = new ContactOperations(client);		
        Calendar createdThreshold = Calendar.getInstance();
        createdThreshold.add(Calendar.DATE, -7);		
        MailListOperations listOps = new MailListOperations(client);	
        MailListObject list = listOps.get(new MailListReadRequest().withName("Test Seed"));		
        MessageOperations msgOps = new MessageOperations(client);
		ObjectOperations<DeliveryObject> deliveryOps = client.transport(DeliveryObject.class);
		
		ContactObject contact = null;
		if (cmdArgs.get("toEmail") != null && cmdArgs.get("toEmail") != "") {
			tempEmail = cmdArgs.get("toEmail");
		} else {
			if (thisMessage.get("email_addr") != null) {
				tempEmail = thisMessage.get("email_addr");	
			}
		}
		
		if (tempEmail != "" && getContactViaEmail(tempEmail, contactOps) != null) {
			contact = getContactViaEmail(tempEmail, contactOps);
		} else {
			isSendable = false;
		}
		
		if (isSendable) {
				
	        MessageObject message = null;		        
	        if (messageNames.containsKey(thisMessage.get("notification_type"))) {
	        	if ("SV".equals(thisMessage.get("notification_type"))) {
	        		if (messageNames.containsKey(thisMessage.get("idt_sku_color_code_1"))) {
	        			message = msgOps.get(new MessageReadRequest().withName(messageNames.get(thisMessage.get("idt_sku_color_code_1"))));
	        		} else {
		        		log("Improper SVCV color code: " + thisMessage.get("idt_sku_color_code_1"));
			        	isSendable = false;
			        	sendStatus = "FAIL";
		        	}
	        	} else {
	        		message = msgOps.get(new MessageReadRequest().withName(messageNames.get(thisMessage.get("notification_type"))));
	        	}
	        } else {
	        	log("Improper notification type: " + thisMessage.get("notification_type"));
	        	isSendable = false;
	        	sendStatus = "FAIL";
	        }		        		        
			log("Preparing to send Bronto email: " + message.getName());
			
			DeliveryRecipientObject recipient = new DeliveryRecipientObject();
			recipient.setDeliveryType(DeliveryRecipientSelection.SELECTED.getApiValue());
			recipient.setType(DeliveryRecipientType.CONTACT.getApiValue());
			if ( ( cmdArgs.get("test") != null && Boolean.parseBoolean(cmdArgs.get("test")) )&&
												contact.getEmail().indexOf("titlenine.") < 0 ) {
				sendStatus = "SUPPRESS";
				log("Test email attempting to send outside titlenine to " +  contact.getEmail());
				log("Not sent");
			} else if (contact == null) {
				sendStatus = "ERROR";
				log("Contact not sendable : " + tempEmail);
				apiResponse = "Error: Invalid contact";
			} else {
				recipient.setId(contact.getId());
				log("Confirmed ID: " + contact.getId());

				ObjectFactory objectFactory = new ObjectFactory();
				
				GregorianCalendar calendar = new GregorianCalendar();
				XMLGregorianCalendar xmlCalendar = null;
				try {
				     xmlCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
				    debug("xmlCalendar: " + xmlCalendar.toString());
				} catch (DatatypeConfigurationException e) {
				    e.printStackTrace();
				}
				
				DeliveryObject delivery = objectFactory.createDeliveryObject();
					debug("delivery.start: " + delivery.getStart());
					delivery.setStart(xmlCalendar);
					delivery.setType(DeliveryType.TRANSACTIONAL.getApiValue());
					delivery.setMessageId(message.getId());
					if (cmdArgs.get("fromEmail") != null && cmdArgs.get("fromEmail") != "") {
						delivery.setFromEmail(cmdArgs.get("fromEmail"));
					} else {
						delivery.setFromEmail("thefolks@titlenine.com");
					}
					if (cmdArgs.get("fromName") != null && cmdArgs.get("fromName") != "") {
						delivery.setFromEmail(cmdArgs.get("fromName"));
					} else {
						delivery.setFromName("The Folks at Title Nine");
					}
			        delivery.getRecipients().add(recipient);			
				
		        for(Iterator it = thisMessage.keySet().iterator(); it.hasNext();) {
		    		String key = (String) it.next();
		    		if (thisMessage.get(key) != null)
		    			delivery.getFields().add(createField(key, "html", thisMessage.get(key)));		    		
		    	}
		        
		        int deliveryLine = 0;
		        debug("---------------------------------------------");
		        for ( MessageFieldObject field : delivery.getFields() ){        	
		            debug("(dlvryLine #" + ++deliveryLine + ")\t" + field.getName() + " : " + field.getType() + " : " + field.getContent());
		        }
		        debug("---------------------------------------------");
		        
		        try {
		            WriteResult result = deliveryOps.add(delivery);
		            log("Errors: " + result.getErrors().toString());
		            for (ResultItem item : result.getResults()) {
		            	debug("Is Error? " + item.isIsError()); //
		                if (item.isIsError()) {
			            	debug("Item: " + item.toString() + 
			            			" Error: " + item.getErrorCode() + ": " +item.getErrorString());
			                apiResponse += (" Error: " + item.getErrorCode() + ": " +item.getErrorString());
			                sendStatus = "FAIL";
		                } else {
		                	apiResponse += "OK";
		                }
		            }
		        } catch (Exception e){
		            e.printStackTrace();
		        }

		        if (sendStatus == "") {
		        	sendStatus = "SUCCESS";
		        }
			}
			        	
	    } else {
    		log("Email suppressed");
    		sendStatus = "SUPPRESS";
    	}
				
    	if (Boolean.parseBoolean(cmdArgs.get("test"))) {
    		sendStatus += "-TEST";
    	}
    	
    	logToDatabase(tempEmail,thisMessage.get("order_nbr"),thisMessage.get("sold_to_nbr"),
    			fileName,thisMessage.get("notification_type"),sendStatus,apiResponse);
    } // end of sendEmail()
    
    //* recursively check if contact is in the system and if not, call createContact()
    public ContactObject getContactViaEmail(String emailAddr, ContactOperations contactOps) {
    	final ContactReadRequest readContacts = new ContactReadRequest()
        .withEmail(emailAddr);

    	ContactObject contact = null;
    	
    	if (contactOps.read(readContacts).size() > 0) {
			contact = contactOps.read(readContacts).get(0);
		
			debug("Contact found: " + contact.getEmail() + ", " + contact.getId());
			return contact;
		} else {
			if (createContact(emailAddr, contactOps)) {
				return getContactViaEmail(emailAddr, contactOps);
			} else {
				log("Unable to locate contact");
			}
		}
    	return null;
    } // end of getContactViaEmail()
    
    //* attempts to create a transactional contact based on an email address
    public boolean createContact(String emailAddr, ContactOperations contactOps) {
    	
    	debug("Email address not found");
    	ContactObject contact = contactOps.newObject()
				.set("email", emailAddr)
				.set("status", ContactStatus.TRANSACTIONAL)
				.get();
		try {
		    WriteResult result = contactOps.add(contact);
		    for (ResultItem item : result.getResults()) {
		    	if (item.isIsError()) {
		    		log(item.getErrorCode() + " : " + item.getErrorString());
		    		return false;
		    	}
            }
		    log("Added contact with email " + emailAddr);
		    return true;
		} catch (Exception e) {
			e.printStackTrace();			
		}
		log("Unable to create contact");
		return false;
    } // end of createContact()

    //* turns a name/content pair into a MessageFieldObject
    public static MessageFieldObject createField(String name, String type, String content){
        if ( type == null || "".equals(type) ){
            type = "html";
        }
        MessageFieldObject field = new MessageFieldObject();
        field.setName(name);
        field.setType(type);
        field.setContent(content);
        return field;
    } // end of createField()
    
    //* turns XML from CW into key/value pairs in a LinkedHashMap
    public MutableBoolean parseXML (LinkedHashMap thisMessage, File file, /* String filePath,*/ LinkedHashMap carriers, 
    			LinkedHashMap<String, OrderMessage> nonInvItems, LinkedHashMap<String, String> utilItems) {
    	MutableBoolean sendIndicator = new MutableBoolean(true);
    	List<OrderMessage> addlOrderMessages = new ArrayList<OrderMessage>();
    	log("Parsing file");
    	String certonaItems = "";
        try{
        	JAXBContext jContext = JAXBContext.newInstance(Message.class);
            Unmarshaller unmarshallerObj = jContext.createUnmarshaller();
            Message message = (Message) unmarshallerObj.unmarshal(file);
            
            if (message != null) {
            	putMessageData(thisMessage, message);
            	
            	if (message.getEmail() != null) {
            		Email email = message.getEmail();
            		putEmailData(thisMessage, email);
            
            		if (email.getCustomerSoldTo() != null) {
            			CustomerSoldTo customerSoldTo = email.getCustomerSoldTo();
            			putCustomerSoldToData(thisMessage, customerSoldTo);
            		
            			if (customerSoldTo.getCustomerSoldToHistory() != null) {
            				CustomerSoldToHistory customerSoldToHistory = customerSoldTo.getCustomerSoldToHistory();
            				putCustomerSoldToHistory(thisMessage, customerSoldToHistory);
            			}
            			
            			if (email.getCustomerShipTo() != null) {
            				CustomerShipTo customerShipTo = email.getCustomerShipTo();
            				putCustomerShipTo(thisMessage, customerShipTo);
            			}
            			
            			if (email.getOrder() != null) {
            				Order order = email.getOrder();
            				putOrder(thisMessage, order);
            
            				if (order.getOrderDetails() != null) {          
				            	List<OrderDetail> orderDetails = order.getOrderDetails().getOrderDetail();
				                if (orderDetails != null)
				                	certonaItems = putOrderDetails(thisMessage, orderDetails, certonaItems);
				            }
            
				            if (order.getOrderPayments() != null) {
				            	OrderPayments orderPayments = order.getOrderPayments();
				            	if (orderPayments.getOrderPaymentCount() != null)
				            		thisMessage.put("order_payment_count", orderPayments.getOrderPaymentCount());
				            	List<OrderPayment> orderPaymentList = orderPayments.getOrderPayment();
				            	if (orderPaymentList != null)
				            		putOrderPayments(thisMessage, orderPaymentList);
				            }
            
				            if (order.getInvoice() != null) {
				            	Invoice invoice = order.getInvoice();
				            	putInvoice(thisMessage, invoice, carriers);
				            	List<InvoiceDetail> invoiceDetails = invoice.getInvoiceDetails().getInvoiceDetail();
				            	if (invoiceDetails != null) {
					            	certonaItems = putInvoiceDetails(thisMessage, invoiceDetails, certonaItems, 
					            				nonInvItems, utilItems, addlOrderMessages, sendIndicator);
				            	}
				            	if (invoice.getTrackingNumbers() != null) {
				            		List<TrackingNumber> trackingNumbers = invoice.getTrackingNumbers().getTrackingNumber();
				            		putTrackingNumbers(thisMessage, trackingNumbers, carriers);
				            	}
				            		
				            }
				            
				            if (order.getOrderMessages() != null || addlOrderMessages.size() > 0) {
				            	List<OrderMessage> orderMessages = null;
				            	if (order.getOrderMessages() != null ) 
				            		orderMessages = order.getOrderMessages().getOrderMessage();
				            	putOrderMessages(thisMessage, orderMessages, addlOrderMessages);
				            }
            			}
            
			            if (email.getEmailTemplateText().getBeforeBodyTextLines() != null) {
			            	List<BeforeBodyTextLine> beforeBodyTextLines = email.getEmailTemplateText().getBeforeBodyTextLines().getBeforeBodyTextLine();
			            	putBeforeBodyTextLines(thisMessage, beforeBodyTextLines);
			            }
			            
			            if (email.getEmailTemplateText().getAfterBodyTextLines() != null) {
			            	List<AfterBodyTextLine> afterBodyTextLines = email.getEmailTemplateText().getAfterBodyTextLines().getAfterBodyTextLine();
			            	putAfterBodyTextLines(thisMessage, afterBodyTextLines);
			            }
            		}
            	}
            }
            
            if (certonaItems != "")
            	thisMessage.put("cw_certona_items", certonaItems);
           
            printAllData(thisMessage);
            
        }catch(Exception e){
            e.printStackTrace();
            sendIndicator.setValue(false);
        }
        return sendIndicator;
    } // end of parseXML()
    
    //* format an amount of pennies into currency
    public static String parseCurrency(String val) {
    	if (val != null) {
	    	try{
	    		DecimalFormat formatter = new DecimalFormat("$#,##0.00;-$#,##0.00");
	    		return formatter.format(Double.parseDouble(val) / 100);
	    	} catch (NumberFormatException e) {}
    	}
    	return null;
    } // end of parseCurrency()
    
    //* format a CW style date into a more standard one
    public static String parseDate(String dateIn) {
    	if (dateIn != null) {
	    	try{        	
	        	DateFormat dfIn = new SimpleDateFormat("yyyy-MM-dd");
	        	DateFormat dfOut = new SimpleDateFormat("M/d/yyyy");
	    		Date dateOut = dfIn.parse(dateIn);
	    		return dfOut.format(dateOut);
	    	} catch (Exception e) {}
    	}
    	return null;
    } // end of parseDate()
    
    //* format a 24-hour time into an am/pm one
    public static String parseTime (String timeIn) {
    	if (timeIn != null) {
	    	try{
	    		SimpleDateFormat tfIn = new SimpleDateFormat("HH:mm:ss");
	    		SimpleDateFormat tfOut = new SimpleDateFormat("h:mm:ss a");
	    		Date timeOut = tfIn.parse(timeIn);
	    		return tfOut.format(timeOut);
	    	} catch (Exception e) {}
    	}
    	return null;
    } // end of parseTime()

    //* tests for null before placing data in a LinkedHashMap
    public static void putData(LinkedHashMap thisMessage, String keyName, String keyValue) {
    	if(keyValue != null && keyName != null)
    		thisMessage.put(keyName, keyValue);
    } // end of putData()
    
    //* processes all fields of the Message object
    public static void putMessageData(LinkedHashMap thisMessage, Message message) {
    	putData(thisMessage, "source", message.getSource());
    	putData(thisMessage, "target", message.getTarget());
    	putData(thisMessage, "type", message.getType());
    	putData(thisMessage, "date_created", parseDate(message.getDateCreated()));
    	putData(thisMessage, "time_created", parseTime(message.getTimeCreated()));
    } // end of putMessageData()
    
    //* processes all fields of the Email object
    public static void putEmailData(LinkedHashMap thisMessage, Email email) {
    	putData(thisMessage, "notification_type", email.getNotificationType());
    	putData(thisMessage, "notification_type_desc", email.getNotificationTypeDesc());
    	putData(thisMessage, "email_addr", email.getEmailAddr());
    	putData(thisMessage, "company", email.getCompany());
    	putData(thisMessage, "company_desc", email.getCompanyDesc());
    	putData(thisMessage, "entity", email.getEntity());
    	putData(thisMessage, "entity_desc", email.getEntityDesc());
    } // end of putEmailData()
    
    //* processes all fields of the CustomerSoldTo object
    public static void putCustomerSoldToData(LinkedHashMap thisMessage, CustomerSoldTo customerSoldTo) {
    	putData(thisMessage, "sold_to_nbr", customerSoldTo.getSoldToNbr());
    	putData(thisMessage, "sold_to_company", customerSoldTo.getSoldToCompany());
    	putData(thisMessage, "sold_to_fname", customerSoldTo.getSoldToFname());
    	putData(thisMessage, "eGiftCard_sender_fname", customerSoldTo.getSoldToFname());
    	putData(thisMessage, "sold_to_minitial", customerSoldTo.getSoldToMinitial());
    	putData(thisMessage, "sold_to_lname", customerSoldTo.getSoldToLname());
    	putData(thisMessage, "eGiftCard_sender_lname", customerSoldTo.getSoldToLname());
    	putData(thisMessage, "sold_to_addr1", customerSoldTo.getSoldToAddr1());
    	putData(thisMessage, "sold_to_addr2", customerSoldTo.getSoldToAddr2());
    	putData(thisMessage, "sold_to_addr3", customerSoldTo.getSoldToAddr3());
    	putData(thisMessage, "sold_to_addr4", customerSoldTo.getSoldToAddr4());
    	putData(thisMessage, "sold_to_city", customerSoldTo.getSoldToCity());
    	putData(thisMessage, "sold_to_state", customerSoldTo.getSoldToState());
    	putData(thisMessage, "sold_to_state_name", customerSoldTo.getSoldToStateName());
    	putData(thisMessage, "sold_to_postal", customerSoldTo.getSoldToPostal());
    	putData(thisMessage, "sold_to_country", customerSoldTo.getSoldToCountry());
    	putData(thisMessage, "sold_to_country_name", customerSoldTo.getSoldToCountryName());
    	putData(thisMessage, "sold_to_alternate_id", customerSoldTo.getSoldToAlternateId());
    	putData(thisMessage, "sold_to_cust_class", customerSoldTo.getSoldToCustClass());
    	putData(thisMessage, "sold_to_cust_class_desc", customerSoldTo.getSoldToCustClassDesc());	
    } // end of putCustomerSoldToData()
    
    //* processes all fields of the CustomerSoldToHistory object
    public static void putCustomerSoldToHistory(LinkedHashMap thisMessage, CustomerSoldToHistory customerSoldToHistory) {
    	putData(thisMessage, "sold_to_LTD_nbr_orders", customerSoldToHistory.getSoldToLTDNbrOrders());
    	putData(thisMessage, "sold_to_LTD_order_dollars", parseCurrency(customerSoldToHistory.getSoldToLTDOrderDollars()));
    	putData(thisMessage, "sold_to_LTD_nbr_sales", customerSoldToHistory.getSoldToLTDNbrSales());
    	putData(thisMessage, "sold_to_LTD_sales_dollars", parseCurrency(customerSoldToHistory.getSoldToLTDSalesDollars()));
    	putData(thisMessage, "sold_to_first_buyer", customerSoldToHistory.getSoldToFirstBuyer());
    	putData(thisMessage, "sold_to_last_src", customerSoldToHistory.getSoldToLastSrc());
    	putData(thisMessage, "sold_to_last_src_desc", customerSoldToHistory.getSoldToLastSrcDesc());
    } // end of putCustomerSoldToHistory()
    
    //* processes all fields of the CustomerShipTo object
    public static void putCustomerShipTo(LinkedHashMap thisMessage, CustomerShipTo customerShipTo) {
    	putData(thisMessage, "ship_to_company", customerShipTo.getShipToCompany());
    	putData(thisMessage, "ship_to_fname", customerShipTo.getShipToFname());
    	putData(thisMessage, "eGiftCard_recipient_fname", customerShipTo.getShipToFname());
    	putData(thisMessage, "ship_to_minitial", customerShipTo.getShipToMinitial());
    	putData(thisMessage, "ship_to_lname", customerShipTo.getShipToLname());
    	putData(thisMessage, "eGiftCard_recipient_lname", customerShipTo.getShipToLname());
    	putData(thisMessage, "ship_to_addr1", customerShipTo.getShipToAddr1());
    	putData(thisMessage, "ship_to_addr2", customerShipTo.getShipToAddr2());
    	putData(thisMessage, "ship_to_addr3", customerShipTo.getShipToAddr3());
    	putData(thisMessage, "ship_to_addr4", customerShipTo.getShipToAddr4());
    	putData(thisMessage, "ship_to_city", customerShipTo.getShipToCity());
    	putData(thisMessage, "ship_to_state", customerShipTo.getShipToState());
    	putData(thisMessage, "ship_to_state_name", customerShipTo.getShipToStateName());
    	putData(thisMessage, "ship_to_postal", customerShipTo.getShipToPostal());
    	putData(thisMessage, "ship_to_country", customerShipTo.getShipToCountry());
    	putData(thisMessage, "ship_to_country_name", customerShipTo.getShipToCountryName());
    } // end of putCustomerShipTo()
    
    //* processes all fields of the Order object
    public static void putOrder(LinkedHashMap thisMessage, Order order) {
    	putData(thisMessage, "order_nbr", order.getOrderNbr());
    	putData(thisMessage, "order_date", parseDate(order.getOrderDate()));
    	putData(thisMessage, "order_source", order.getOrderSource());
    	putData(thisMessage, "order_source_desc", order.getOrderSourceDesc());
    	putData(thisMessage, "order_offer", order.getOrderOffer());
    	putData(thisMessage, "order_offer_desc", order.getOrderOfferDesc());
    	putData(thisMessage, "order_process_date", parseDate(order.getOrderProcessDate()));
    	putData(thisMessage, "order_ship_to", order.getOrderShipTo());
    	putData(thisMessage, "ost_discount", parseCurrency(order.getOstDiscount()));
    	putData(thisMessage, "ost_tax", parseCurrency(order.getOstTax()));
    	putData(thisMessage, "ost_merch", parseCurrency(order.getOstMerch()));
    	putData(thisMessage, "ost_freight", parseCurrency(order.getOstFreight()));
    	putData(thisMessage, "ost_addl_freight", parseCurrency(order.getOstAddlFreight()));
    	putData(thisMessage, "ost_hand", parseCurrency(order.getOstHand()));
    	putData(thisMessage, "ost_addl_charge", parseCurrency(order.getOstAddlCharge()));
    	putData(thisMessage, "ost_total_amt", parseCurrency(order.getOstTotalAmt()));
    } // end of putOrder()
    
    //* processes all fields of all OrderDetail objects
    public static String putOrderDetails(LinkedHashMap thisMessage, List<OrderDetail> orderDetails, String certonaItems) {
    	int odtIndex = 0;
    	for (OrderDetail orderDetail : orderDetails) {
    		odtIndex++;
    		if (orderDetail.getOdtItem() != null) {    		
    			thisMessage.put("odt_item_" + odtIndex, orderDetail.getOdtItem());
    			if (certonaItems != "")
    				certonaItems += ";";
    			certonaItems += orderDetail.getOdtItem();
    		}    		
    		putData(thisMessage, "odt_item_desc_" + odtIndex, orderDetail.getOdtItemDesc());
	    	if (orderDetail.getOdtSKU() != null) {
	    		thisMessage.put("odt_SKU_" + odtIndex, orderDetail.getOdtSKU());
	    		if ("SVCV".equalsIgnoreCase((String)thisMessage.get("odt_item_" + odtIndex)) ||
	    				"SVCW".equalsIgnoreCase((String)thisMessage.get("odt_item_" + odtIndex))) {
	    			thisMessage.put("odt_sku_color_code_" + odtIndex, orderDetail.getOdtSKU().split(" ")[2].toLowerCase());
	    		} else {
	    			thisMessage.put("odt_sku_color_code_" + odtIndex, orderDetail.getOdtSKU().split(" ")[0].toLowerCase());
	    		}
	    	}
	    	putData(thisMessage, "odt_SKU_desc_" + odtIndex, orderDetail.getOdtSKUDesc());
	    	putData(thisMessage, "odt_alias_item_" + odtIndex, orderDetail.getOdtAliasItem());
	    	putData(thisMessage, "odt_item_class_" + odtIndex, orderDetail.getOdtItemClass());
	    	putData(thisMessage, "odt_item_class_desc_" + odtIndex, orderDetail.getOdtItemClassDesc());
	    	putData(thisMessage, "odt_set_master_" + odtIndex, orderDetail.getOdtSetMaster());
	    	putData(thisMessage, "odt_set_component_seq_" + odtIndex, orderDetail.getOdtSetComponentSeq());
	    	putData(thisMessage, "odt_free_gift_" + odtIndex, orderDetail.getOdtFreeGift());
	    	putData(thisMessage, "odt_offer_" + odtIndex, orderDetail.getOdtOffer());
	    	putData(thisMessage, "odt_offer_desc_" + odtIndex, orderDetail.getOdtOfferDesc());
	    	putData(thisMessage, "odt_source_" + odtIndex, orderDetail.getOdtSource());
	    	putData(thisMessage, "odt_source_desc_" + odtIndex, orderDetail.getOdtSourceDesc());
	    	putData(thisMessage, "odt_price_" + odtIndex, parseCurrency(orderDetail.getOdtPrice()));
	    	putData(thisMessage, "odt_qty_" + odtIndex, orderDetail.getOdtQty());
	    	putData(thisMessage, "odt_extended_price_" + odtIndex, parseCurrency(orderDetail.getOdtExtendedPrice()));
	    	putData(thisMessage, "odt_availability_msg_" + odtIndex, orderDetail.getOdtAvailabilityMsg());
	    	putData(thisMessage, "odt_expected_ship_date_" + odtIndex, parseDate(orderDetail.getOdtExpectedShipDate()));
	    	putData(thisMessage, "odt_seq_nbr_" + odtIndex, orderDetail.getOdtSeqNbr());
	    	putData(thisMessage, "odt_line_nbr_" + odtIndex, orderDetail.getOdtLineNbr());
	    	putData(thisMessage, "odt_backorder_qty_" + odtIndex, orderDetail.getOdtBackorderQty());
	    	putData(thisMessage, "odt_soldout_qty_" + odtIndex, orderDetail.getOdtSoldoutQty());
    	}
    	return certonaItems;
    } // end of putOrderDetails()
    
    //* processes all fields of all OrderPayment objects
    public static void putOrderPayments(LinkedHashMap thisMessage, List<OrderPayment> orderPaymentList) {
    	int opmIndex = 0;
    	for (OrderPayment orderPayment : orderPaymentList) {
    		opmIndex++;
    		putData(thisMessage, "opm_payment_type_" + opmIndex, orderPayment.getOpmPaymentType());
    		if ("13".equals(orderPayment.getOpmPaymentType()))
    			putData(thisMessage, "opm_payment_type_desc_" + opmIndex, "NO CHARGE");
    		else
    			putData(thisMessage, "opm_payment_type_desc_" + opmIndex, orderPayment.getOpmPaymentTypeDesc());
    		putData(thisMessage, "opm_pay_plan_" + opmIndex, orderPayment.getOpmPayPlan());
    		putData(thisMessage, "opm_pay_plan_desc_" + opmIndex, orderPayment.getOpmPayPlanDesc());
    		putData(thisMessage, "opm_deferral_days_" + opmIndex, orderPayment.getOpmDeferralDays());
    		putData(thisMessage, "opm_deferral_date_" + opmIndex, parseDate(orderPayment.getOpmDeferralDate()));
    		putData(thisMessage, "opm_nbr_installments_" + opmIndex, orderPayment.getOpmNbrInstallments());
    		putData(thisMessage, "opm_installment_interval_" + opmIndex, orderPayment.getOpmInstallmentInterval());
    		putData(thisMessage, "opm_gift_certificate_cpn_" + opmIndex, orderPayment.getOpmGiftCertificateCpn());
    		putData(thisMessage, "opm_credit_card_" + opmIndex, orderPayment.getOpmCreditCard());
    		putData(thisMessage, "opm_credit_card_exp_date_" + opmIndex, orderPayment.getOpmCreditCardExpDate());
    	}
    } // end of putOrderPayments()
    
    //* processes all fields of the Invoice object
    public static void putInvoice(LinkedHashMap thisMessage, Invoice invoice, LinkedHashMap carriers) {
    	putData(thisMessage, "invoice_nbr", invoice.getInvoiceNbr());
    	putData(thisMessage, "invoice_ship_to", invoice.getInvoiceShipTo());
    	
    	Carrier carrier = (Carrier)carriers.get(invoice.getInvoiceShipTo());
    	putData(thisMessage, "invoice_ship_to_label", carrier.getLabel());
    	
    	putData(thisMessage, "ist_ship_date", parseDate(invoice.getIstShipDate()));
    	putData(thisMessage, "ist_ship_merch", parseCurrency(invoice.getIstShipMerch()));
    	putData(thisMessage, "ist_ship_tax", parseCurrency(invoice.getIstShipTax()));
    	putData(thisMessage, "ist_ship_freight", parseCurrency(invoice.getIstShipFreight()));
    	putData(thisMessage, "ist_ship_addl_freight", parseCurrency(invoice.getIstShipAddlFreight()));
    	putData(thisMessage, "ist_ship_hand", parseCurrency(invoice.getIstShipHand()));
    	putData(thisMessage, "ist_ship_addl_charge", parseCurrency(invoice.getIstShipAddlCharge()));
    	putData(thisMessage, "ist_ship_total_amt", parseCurrency(invoice.getIstShipTotalAmt()));
    	putData(thisMessage, "cc_credit_amt", parseCurrency(invoice.getCcCreditAmt()));
    } // end of putInvoice()
    
    //* processes all fields of all InvoiceDetail objects, except nonInventoryItems
    public static String putInvoiceDetails(LinkedHashMap thisMessage, List<InvoiceDetail> invoiceDetails, String certonaItems,
    		LinkedHashMap<String, OrderMessage> nonInvItems, LinkedHashMap<String, String> utilItems, List<OrderMessage> addlOrderMessages, MutableBoolean sendIndicator) {
    	int idtIndex = 0;
    	int miscIndex = 0;
    	for (InvoiceDetail invoiceDetail : invoiceDetails) {
    		if ( invoiceDetail.getIdtItem() != null && !nonInvItems.containsKey(invoiceDetail.getIdtItem()) ) {
				sendIndicator.setValue(true);
	    		idtIndex++;
	    		putData(thisMessage, "idt_order_dtl_seq_nbr_" + idtIndex, invoiceDetail.getIdtOrderDtlSeqNbr());
	    		putData(thisMessage, "idt_line_nbr_" + idtIndex, invoiceDetail.getIdtLineNbr());
	    		if (invoiceDetail.getIdtItem() != null) {
		    		thisMessage.put("idt_item_" + idtIndex, invoiceDetail.getIdtItem());
		    		if (certonaItems != "")
	    				certonaItems += ";";
		    		certonaItems += invoiceDetail.getIdtItem();
	    		}
	    		putData(thisMessage, "idt_item_desc_" + idtIndex, invoiceDetail.getIdtItemDesc());
	    		if (invoiceDetail.getIdtSKU() != null) {
		    		thisMessage.put("idt_SKU_" + idtIndex, invoiceDetail.getIdtSKU());
		    		if ("SVCV".equalsIgnoreCase((String)thisMessage.get("idt_item_" + idtIndex)) ||
		    				"SVCW".equalsIgnoreCase((String)thisMessage.get("idt_item_" + idtIndex))) {
		    			thisMessage.put("idt_sku_color_code_" + idtIndex, invoiceDetail.getIdtSKU().split(" ")[2].toLowerCase());
		    		} else {
		    			thisMessage.put("idt_sku_color_code_" + idtIndex, invoiceDetail.getIdtSKU().split(" ")[0].toLowerCase());
		    		}
	    		}
	    		putData(thisMessage, "idt_SKU_desc_" + idtIndex, invoiceDetail.getIdtSKUDesc());
	    		putData(thisMessage, "idt_item_class_" + idtIndex, invoiceDetail.getIdtItemClass());
	    		putData(thisMessage, "idt_item_class_desc_" + idtIndex, invoiceDetail.getIdtItemClassDesc());
	    		putData(thisMessage, "idt_alias_item_" + idtIndex, invoiceDetail.getIdtAliasItem());	    		
	    		putData(thisMessage, "idt_ship_qty_" + idtIndex, invoiceDetail.getIdtShipQty());
	    		putData(thisMessage, "idt_price_" + idtIndex, parseCurrency(invoiceDetail.getIdtPrice()));
	    		putData(thisMessage, "idt_extended_price_" + idtIndex, parseCurrency(invoiceDetail.getIdtExtendedPrice()));
	    		if (utilItems.containsKey(invoiceDetail.getIdtItem())) {
	    			putData(thisMessage, "idt_is_util_" + idtIndex, "Y");
	    		} else {
	    			putData(thisMessage, "idt_is_util_" + idtIndex, "N");
	    		}
	    		
	    		if ("SVCV".equalsIgnoreCase(invoiceDetail.getIdtItem())) {
	    				List<StoredValueCard> storedValueCards = invoiceDetail.getStoredValueCards().getStoredValueCard();
	    				putStoredValueCards(thisMessage, storedValueCards);
	    		}
    		} else {
    			idtIndex++;
    			if (idtIndex == 1) {
    				if ( "X".equals(nonInvItems.get(invoiceDetail.getIdtItem()).getOmsPrintFlag()) ) {
    				sendIndicator.setValue(false);
    				} else if ( !"X".equals(nonInvItems.get(invoiceDetail.getIdtItem()).getOmsPrintFlag()) ) {
    					sendIndicator.setValue(true);
    					addlOrderMessages.add(nonInvItems.get(invoiceDetail.getIdtItem()));
    				}
    			} else {
    				if ( !"X".equals(nonInvItems.get(invoiceDetail.getIdtItem()).getOmsPrintFlag()) ) {
    					sendIndicator.setValue(true);
    					addlOrderMessages.add(nonInvItems.get(invoiceDetail.getIdtItem()));
    				}
    			}
    		}
    	}
    	return certonaItems;
    } // end of putInvoiceDetails()
    
    //* processes all fields of all StoredValueCard objects
    public static void putStoredValueCards(LinkedHashMap thisMessage, List<StoredValueCard> storedValueCards) {
    	int svcIndex = 0;
    	for (StoredValueCard storedValueCard : storedValueCards) {
    		svcIndex++;
    		putData(thisMessage, "svc_card_nbr_" + svcIndex, storedValueCard.getSvcCardNbr());
    		putData(thisMessage, "svc_card_type_" + svcIndex, storedValueCard.getSvcCardType());
    		putData(thisMessage, "svc_issue_amt_" + svcIndex, parseCurrency(storedValueCard.getSvcIssueAmt()));
    		putData(thisMessage, "svc_activation_date_" + svcIndex, parseDate(storedValueCard.getSvcActivationDate()));
    		putData(thisMessage, "svc_activation_time_" + svcIndex, parseTime(storedValueCard.getSvcActivationTime()));
    	}
    } // end of putStoredValueCards()
    
    //* processes all fields of all TrackingNumber objects
    public static void putTrackingNumbers(LinkedHashMap thisMessage, List<TrackingNumber> trackingNumbers, LinkedHashMap carriers) {
    	int trnIndex = 0;
    	for (TrackingNumber trackingNumber : trackingNumbers) {
    		trnIndex++;
    		putData(thisMessage, "tracking_nbr_" + trnIndex, trackingNumber.getTrackingNbr());
    		putData(thisMessage, "tracking_ship_via_" + trnIndex, trackingNumber.getTrackingShipVia());
    		putData(thisMessage, "tracking_ship_via_desc_" + trnIndex, trackingNumber.getTrackingShipViaDesc());
    		
    		Carrier carrier = (Carrier)carriers.get(trackingNumber.getTrackingShipVia());
    		
    		putData(thisMessage, "tracking_carrier_" + trnIndex, carrier.getName());
    		
    		if (trackingNumber.getTrackingNbr() != null && carrier.getUrl() != null && carrier.getUtm() != null) {
    			thisMessage.put("tracking_url_" + trnIndex, carrier.getUrl() + carrier.getUtm() + trackingNumber.getTrackingNbr());
    		} else {
    			putData(thisMessage, "tracking_url_" + trnIndex, carrier.getUrl()); 
    		}
    		
    		putData(thisMessage, "tracking_desc_" + trnIndex, carrier.getLabel());
    	}
    } // end of putTrackingNumbers()
    
    //* processes all fields of all OrderMessage objects
    public static void putOrderMessages(LinkedHashMap thisMessage, List<OrderMessage> orderMessages, List<OrderMessage> addlOrderMessages) {
    	int omsIndex = 0;
    	boolean hasGiftMessage = false;
    	if (orderMessages != null) {
	    	for (OrderMessage orderMessage : orderMessages) {
	    		omsIndex++;
	    		putData(thisMessage, "oms_seq_nbr_" + omsIndex, orderMessage.getOmsSeqNbr());
	    		putData(thisMessage, "oms_message_" + omsIndex, orderMessage.getOmsMessage());
	    		putData(thisMessage, "oms_print_flag_" + omsIndex, orderMessage.getOmsPrintFlag());
	    		if ( "G".equals(orderMessage.getOmsPrintFlag()) ) {
	    			hasGiftMessage = true;
	    		}
	    	}
    	}
    	putData(thisMessage, "has_gift_message", hasGiftMessage ? "Y" : "N");
    	if (addlOrderMessages != null && addlOrderMessages.size() > 0) {
	    	for (OrderMessage addlOrderMessage : addlOrderMessages) {
	    		omsIndex++;
	    		putData(thisMessage, "oms_seq_nbr_" + omsIndex, Integer.toString(omsIndex));
	    		putData(thisMessage, "oms_message_" + omsIndex, addlOrderMessage.getOmsMessage());
	    		putData(thisMessage, "oms_print_flag_" + omsIndex, addlOrderMessage.getOmsPrintFlag());
	    	}
    	}
    } // end of putOrderMessages()
    
    //* processes all fields of all BeforeBodyTextLine objects
    public static void putBeforeBodyTextLines(LinkedHashMap thisMessage, List<BeforeBodyTextLine> beforeBodyTextLines) {
    	int befIndex = 0;
    	for (BeforeBodyTextLine beforeBodyTextLine : beforeBodyTextLines) {
    		befIndex++;
    		putData(thisMessage, "before_line_msg_" + befIndex, beforeBodyTextLine.getBeforeLineMsg());
    	}
    } // end of putBeforeBodyTextLines()
    
    //* processes all fields of all AfterBodyTextLine objects
    public static void putAfterBodyTextLines(LinkedHashMap thisMessage, List<AfterBodyTextLine> afterBodyTextLines) {
    	int aftIndex = 0;
    	for (AfterBodyTextLine afterBodyTextLine : afterBodyTextLines) {
    		aftIndex++;
    		putData(thisMessage, "after_line_msg_" + aftIndex, afterBodyTextLine.getAfterLineMsg());
    	}
    } // end of putAfterBodyTextLines()
    
    
    //* displays contents of the LinkedHashMap (ostensibly, all the XML data) in the console
    public void printAllData(LinkedHashMap thisMessage) {
    	int lineNumber = 0;
    	debug("---------------------------------------------");
    	for(Iterator it = thisMessage.keySet().iterator(); it.hasNext();) {
    		String key = (String) it.next();
    		if (thisMessage.get(key) != null)
    			debug("(mapLine #" + ++lineNumber + ")\t" + key + " = " + thisMessage.get(key));
    		
    	}
    	debug("---------------------------------------------");
    } // end of printAllData()

    //* console logging mechanics
	protected void log(String msg, Exception e){
		if ( "log".equalsIgnoreCase(debugLevel) ||
				"debug".equalsIgnoreCase(debugLevel) ){
			System.out.println("CwToBronto_" + outputLog + ": " + msg);
			if ( e != null )
				System.out.println("CwToBronto_" + outputLog + ": " + "! " + e.toString());
		}
	}

	protected void debug(String msg, Exception e){
		if ( "debug".equalsIgnoreCase(debugLevel) ){
			log(msg, e);
		}
	}

	protected void log(String msg){
		log(msg, null);
	}

	protected void debug(String msg){
		debug(msg, null);
	}

	protected void log(Exception e){
		log("Exception: ", e);
	}

	protected void debug(Exception e){
		debug("Exception: ", e);
	}
	
	protected void log(Collection<String> c) {
		for(String item : c)
			log(item);
	}
	
	protected void debug(Collection<String> c) {
		for(String item : c)
			debug(item);
	} // end of loggers
	
	//* sends select data to the logging database
	public boolean logToDatabase(String email, String orderNum, String customerNum, String sourceFile, String emailType, String status, String apiResponse) {
		debug("Validating database input");
		try {
			email = validateEmail(email);
			orderNum = validateOrderNum(orderNum);
			customerNum = validateCustNum(customerNum);
			sourceFile = validateSourceFile(sourceFile);
			emailType = validateEmailType(emailType);
			status = validateStatus(status);
			apiResponse = validateApiResponse(apiResponse);
			String updateQuery = "insert into TRANSACT_EMAIL_API_LOG (email_address,order_num,customer_num,source_file,email_type,status,api_response) values " +
					"('" + email + "','" + orderNum + "','" + customerNum + "','" + sourceFile + "', '" + emailType + 
					"', '" + status + "', '" + apiResponse + "')";
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
			String url = "jdbc:sqlserver://10.0.100.43;database=TransactionalEmail;username=api_log;password={dffTpc6EakMWV2ZLJ7yd}";
			Connection conn = DriverManager.getConnection(url);
			Statement cs = conn.createStatement();
			cs.execute(updateQuery);
			cs.close();
			conn.close();
			int dbLine = 1;
			debug("(dbInfo #" + dbLine++ + ")\tEmail = " + email);
			debug("(dbInfo #" + dbLine++ + ")\tOrder Number = " + orderNum);
			debug("(dbInfo #" + dbLine++ + ")\tCustomer Number = " + customerNum);
			debug("(dbInfo #" + dbLine++ + ")\tSource File = " + sourceFile);
			debug("(dbInfo #" + dbLine++ + ")\tEmail Type = " + emailType);
			debug("(dbInfo #" + dbLine++ + ")\tSend Status = " + status);
			debug("(dbInfo #" + dbLine++ + ")\tAPI Response = " + apiResponse);
			log("Logging database updated");
			return true;
		} catch (Exception e){
			log("Write to DB log failure!", e);
		}
		return false;
	} // end of logToDatabase()
	
	//* checks length of data and truncates if necessary
	public String validateEmail(String email) {
		if (email == null) {
			return "";
		} else if (email.length() > 100) {
			String validEmail = email.substring(0, 96) + "...";
			debug("Email trimmed from \"" + email + "\" to \"" + validEmail + "\"");
			return validEmail;
		}
		return email;
	}
	
	public String validateOrderNum(String orderNum) {
		if (orderNum == null) {
			return "";
		} else if (orderNum.length() > 20) {
			String validOrderNum = orderNum.substring(0, 16) + "...";
			debug("Order Number trimmed from \"" + orderNum + "\" to \"" + validOrderNum + "\"");
			return validOrderNum;
		}
		return orderNum;
	}
	
	public String validateCustNum(String custNum) {
		if (custNum == null) {
			return "";
		} else if (custNum.length() > 20) {
			String validCustNum = custNum.substring(0, 16) + "...";
			debug("Customer Number trimmed from \"" + custNum + "\" to \"" + validCustNum + "\"");
			return validCustNum;
		} 
		return custNum;
	}
	
	public String validateSourceFile(String sourceFile) {
		if (sourceFile == null) {
			return "";
		} else if (sourceFile.length() > 100) {
			String validSourceFile = sourceFile.substring(0, 96) + "...";
			debug("Source File trimmed from \"" + sourceFile + "\" to \"" + validSourceFile + "\"");
			return validSourceFile;
		}
		return sourceFile;
	}
	
	public String validateEmailType(String emailType) {
		if (emailType == null) {
			return "";
		} else if (emailType.length() > 2) {
			String validEmailType = emailType.substring(0, 1) + "...";
			debug("Email Type trimmed from \"" + emailType + "\" to \"" + validEmailType + "\"");
			return validEmailType;
		} 
		return emailType;
	}
	
	public String validateStatus(String status) {
		if (status == null) {
			return "";
		} else if (status.length() > 20) {
			String validStatus = status.substring(0, 16) + "...";
			debug("Send Status trimmed from \"" + status + "\" to \"" + validStatus + "\"");
			return validStatus;
		}
		return status;
	}
	
	public String validateApiResponse(String apiResponse) {
		if (apiResponse == "") {
			return "Error: Not sent to Bronto";
		} else if (apiResponse.length() > 200) {
			String validApiResponse = apiResponse.substring(0, 196) + "...";
			debug("API Response trimmed from \"" + apiResponse + "\" to \"" + validApiResponse + "\"");
			return validApiResponse;
		} 
		return apiResponse;
	} // end of validators

}
