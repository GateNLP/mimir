package gate.mimir.web.gwt

class GwtService {

  // we don't want to autowire this as it's a servlet and we don't want to
  // configure it in Spring
  GwtServlet gwtServlet = new GwtServlet()

  void handleRpc(request, response) {
    gwtServlet.doPost(request, response)
    response.outputStream.flush()
  }
}
