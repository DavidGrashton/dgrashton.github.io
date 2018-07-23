package com.titlenine.integration.bronto;

//proprietary imports omitted
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

    static final String TOKEN = /*omitted*/;
    static final String WSDL = /*omitted*/;
    static final String URL = /*omitted*/;
    
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
        messageNames.put("SC", "CwShipConfirmation");
        messageNames.put("SV", "Fail");
	//additional codes omitted
    }
    
    //* take items that will come out of CW as invoice items but should not be treated as such and put them in a LinkedHashMap
    public void createNonInvItems(LinkedHashMap<String, OrderMessage> nonInvItems) {
    	nonInvItems.put("2200", new OrderMessage("Thank you for your exchange order!", "CS"));
    	nonInvItems.put("GFTCRD", new OrderMessage("", "X"));
	//additional codes omitted
    } // end of createNonInvItems()
    
    //* put carrier data in a LinkedHashMap
    public static void createCarriers(LinkedHashMap<String, Carrier> carriers) {    	
    	carriers.put("13", new Carrier("13", "USPS", USPSURL, "FIRST CLASS ENVELOPE", USPSQP)); 
	//additional codes omitted
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
					delivery.setFromEmail(/*email string omitted*/);
				}
				if (cmdArgs.get("fromName") != null && cmdArgs.get("fromName") != "") {
					delivery.setFromEmail(cmdArgs.get("fromName"));
				} else {
					delivery.setFromName(/*name string omitted*/);
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
				debug("Is Error? " + item.isIsError());
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
    
    //similar methods omitted
    
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
	    	//additional fields omitted
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
    		//additional fields omitted
    	}
    } // end of putOrderPayments()
    
    //* processes all fields of the Invoice object
    public static void putInvoice(LinkedHashMap thisMessage, Invoice invoice, LinkedHashMap carriers) {
    	putData(thisMessage, "invoice_nbr", invoice.getInvoiceNbr());
    	putData(thisMessage, "invoice_ship_to", invoice.getInvoiceShipTo());
    	
    	Carrier carrier = (Carrier)carriers.get(invoice.getInvoiceShipTo());
    	putData(thisMessage, "invoice_ship_to_label", carrier.getLabel());
    	
    	putData(thisMessage, "ist_ship_date", parseDate(invoice.getIstShipDate()));
    	//additional fields omitted
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
	    		putData(thisMessage, "idt_price_" + idtIndex, parseCurrency(invoiceDetail.getIdtPrice()));
	    		putData(thisMessage, "idt_extended_price_" + idtIndex, parseCurrency(invoiceDetail.getIdtExtendedPrice()));
				//additional fields omitted
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
				//Do not send if all invoice items have omsPrintFlag of "X"
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
