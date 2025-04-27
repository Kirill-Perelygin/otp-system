import org.jsmpp.*;
import org.jsmpp.bean.*;
import org.jsmpp.session.*;

public class SmppService {
    private final String host;
    private final int port;
    private final String systemId;
    private final String password;
    private final String systemType;
    private final TypeOfNumber ton;
    private final NumberingPlanIndicator npi;
    private final String sourceAddress;

    public SmppService(String host, int port, String systemId, String password,
                       String systemType, TypeOfNumber ton, NumberingPlanIndicator npi,
                       String sourceAddress) {
        this.host = host;
        this.port = port;
        this.systemId = systemId;
        this.password = password;
        this.systemType = systemType;
        this.ton = ton;
        this.npi = npi;
        this.sourceAddress = sourceAddress;
    }

    public void sendSms(String phoneNumber, String message) throws Exception {
        SMPPSession session = new SMPPSession();
        try {
            // Подключение к SMPP серверу
            session.connectAndBind(host, port,
                    new BindParameter(BindType.BIND_TX, systemId, password,
                            systemType, ton, npi, null));

            // Отправка сообщения
            String messageId = session.submitShortMessage(
                    "CMT",                    // serviceType
                    ton,                      // sourceAddrTon (TypeOfNumber)
                    npi,                      // sourceAddrNpi (NumberingPlanIndicator)
                    sourceAddress,            // sourceAddr (String)
                    ton,                      // destAddrTon (TypeOfNumber)
                    npi,                      // destAddrNpi (NumberingPlanIndicator)
                    phoneNumber,              // destinationAddr (String)
                    new ESMClass(),           // esmClass
                    (byte)0,                  // protocolId
                    (byte)1,                  // priorityFlag
                    null,                     // scheduleDeliveryTime
                    null,                     // validityPeriod
                    new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT), // registeredDelivery
                    (byte)0,                  // replaceIfPresentFlag
                    new GeneralDataCoding(Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false), // dataCoding
                    (byte)0,                  // smDefaultMsgId
                    message.getBytes()        // shortMessage
            );

            System.out.println("SMS отправлено, ID: " + messageId);
        } finally {
            if (session != null) {
                session.unbindAndClose();
            }
        }
    }
}