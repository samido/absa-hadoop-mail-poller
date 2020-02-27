package foo.bar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import com.google.gson.Gson;

public class MailService {

    @Value("${file.download.location}")
    private String fileDownloadLocation;

    @Value("${mail.folder.name}")
    private String mailFolderName;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void handleMail(MimeMessage message) throws Exception {

        Folder folder = message.getFolder();
        folder.open(Folder.READ_WRITE);
        String messageId = message.getMessageID();
        Message[] messages = folder.getMessages();
        FetchProfile contentsProfile = new FetchProfile();
        contentsProfile.add(FetchProfile.Item.ENVELOPE);
        contentsProfile.add(FetchProfile.Item.CONTENT_INFO);
        contentsProfile.add(FetchProfile.Item.FLAGS);
        folder.fetch(messages, contentsProfile);
        for (int i = 0; i < messages.length; i++) {

            if (((MimeMessage) messages[i]).getMessageID().equals(messageId)) {
                MimeMessage mimeMessage = (MimeMessage) messages[i];

                mimeMessage.setFlag(Flags.Flag.DELETED, true);
                logger.info("SUBJECT: " + mimeMessage.getSubject());
                Address senderAddress = mimeMessage.getFrom()[0];
                logger.info("SENDER " + senderAddress.toString());
                extractDetailsAndDownload(message, mimeMessage);

                MessageMail messageMail1 = new MessageMail();

                messageMail1.setKey(mimeMessage.getContentID());
               // messageMail1.setMimeMessage(mimeMessage);
                messageMail1.setValue("test-mail");
                messageMail1.setHeaders(new HashMap<>());
                messageMail1.setDescription(mimeMessage.getDescription());
                messageMail1.setFileName(mimeMessage.getFileName());
               // messageMail1.getReceivedDate(mimeMessage..getReceivedDate())
                messageMail1.setSubject(mimeMessage.getSubject());
                messageMail1.setMessageNumber(mimeMessage.getMessageNumber());

                postMailToKafka(messageMail1,"TEST-TEST-TEST");

                break;
            }
        }

        Store store = folder.getStore();
        Folder fooFolder = store.getFolder("inbox");
        fooFolder.open(Folder.READ_WRITE);
        fooFolder.appendMessages(new MimeMessage[]{message});
        folder.expunge();
        folder.close(true);
        fooFolder.close(false);
    }


    private void extractDetailsAndDownload(Message message, MimeMessage mimeMessage) throws MessagingException, IOException {
        logger.info("SUBJECT: " + mimeMessage.getSubject());

        Multipart multipart = (Multipart) message.getContent();
        for (int j = 0; j < multipart.getCount(); j++) {

            BodyPart bodyPart = multipart.getBodyPart(j);

            String disposition = bodyPart.getDisposition();

            if (disposition != null && Part.ATTACHMENT.equalsIgnoreCase(disposition)) { // BodyPart.ATTACHMENT doesn't work for gmail
//                Download mail attachments
                logger.info("Mail has some attachments");
                DataHandler handler = bodyPart.getDataHandler();
                logger.info("file name : " + handler.getName());
                ((MimeBodyPart) bodyPart).saveFile(fileDownloadLocation + bodyPart.getFileName());
            } else {
//                Log mail contents
                logger.info("Body: " + bodyPart.getContent());
            }
        }
    }

    private void postMailToKafka(MessageMail msg, String topic){
        try {

            URL url = new URL("http://localhost:8081/"+topic);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

          //  String input = "{\"qty\":100,\"name\":\"iPad 4\"}";

            Gson gson = new Gson();
            String input = gson.toJson(msg);

            System.out.println("INPUT IS HERE................"+input);


            OutputStream os = conn.getOutputStream();
            os.write(input.getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            String output;
            System.out.println("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                System.out.println(output);
            }

            conn.disconnect();

        } catch (MalformedURLException e) {

            e.printStackTrace();

        } catch (IOException e) {

            e.printStackTrace();

        }

    }
}
