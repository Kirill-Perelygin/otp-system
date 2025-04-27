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
            session.connectAndBind(host, port,
                    new BindParameter(BindType.BIND_TX, systemId, password,
                            systemType, ton, npi, null));

            String messageId = session.submitShortMessage(
                    "CMT",
                    ton,
                    npi,
                    sourceAddress,
                    ton,
                    npi,
                    phoneNumber,
                    new ESMClass(),
                    (byte)0,
                    (byte)1,
                    null,
                    null,
                    new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT),
                    (byte)0,
                    new GeneralDataCoding(Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false),
                    (byte)0,
                    message.getBytes()
            );

            System.out.println("SMS sent: " + messageId);
        } finally {
            if (session != null) {
                session.unbindAndClose();
            }
        }
    }
}