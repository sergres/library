package adaptorlib;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class DocumentHandler extends AbstractHandler {
  private static final Logger LOG
      = Logger.getLogger(AbstractHandler.class.getName());

  private GsaCommunicationHandler commHandler;
  private Adaptor adaptor;

  public DocumentHandler(String defaultHostname, Charset defaultCharset,
                         GsaCommunicationHandler commHandler, Adaptor adaptor) {
    super(defaultHostname, defaultCharset);
    this.commHandler = commHandler;
    this.adaptor = adaptor;
  }

  public void meteredHandle(HttpExchange ex) throws IOException {
    String requestMethod = ex.getRequestMethod();
    if ("GET".equals(requestMethod) || "HEAD".equals(requestMethod)) {
      /* Call into adaptor developer code to get document bytes. */
      // TODO(ejona): Need to namespace all docids to allow random support URLs
      DocId docId = commHandler.decodeDocId(getRequestUri(ex));
      LOG.fine("id: " + docId.getUniqueId());

      // TODO(ejona): support different mime types of content
      // TODO(ejona): if text, support providing encoding
      // TODO(ejona): don't retrieve the document contents for HEAD request
      byte content[];
      try {
        content = adaptor.getDocContent(docId);
        if (content == null) {
          throw new IOException("Adaptor did not provide content");
        }
      } catch (FileNotFoundException e) {
        cannedRespond(ex, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
                      "Unknown document: " + e.getMessage());
        return;
      } catch (IOException e) {
        cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                      "IO Exception: " + e.getMessage());
        return;
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Unexpected exception from getDocContent", e);
        cannedRespond(ex, HttpURLConnection.HTTP_INTERNAL_ERROR, "text/plain",
                      "Exception (" + e.getClass().getName() + "): "
                      + e.getMessage());
        return;
      }
      // String contentType = "text/plain"; // "application/octet-stream"
      LOG.finer("processed request; response is size=" + content.length);
      if ("GET".equals(requestMethod))
        respond(ex, HttpURLConnection.HTTP_OK, "text/plain", content);
      else
        respondToHead(ex, HttpURLConnection.HTTP_OK, "text/plain");
    } else {
      cannedRespond(ex, HttpURLConnection.HTTP_BAD_METHOD, "text/plain",
                    "Unsupported request method");
    }
  }
}
