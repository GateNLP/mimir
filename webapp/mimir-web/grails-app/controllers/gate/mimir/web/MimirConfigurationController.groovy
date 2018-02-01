package gate.mimir.web

class MimirConfigurationController {

  static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

  def index() {
    redirect(action: "show")
  }
  
  def show() {
    def mimirConfigurationInstance = MimirConfiguration.findByIndexBaseDirectoryIsNotNull()
    if (!mimirConfigurationInstance) {
      redirect(controller:'mimirStaticPages', action: "admin")
    }
    else {
      [mimirConfigurationInstance: mimirConfigurationInstance]
    }
  }

  def edit() {
    def mimirConfigurationInstance = MimirConfiguration.findByIndexBaseDirectoryIsNotNull()
    if (!mimirConfigurationInstance) {
      mimirConfigurationInstance = new MimirConfiguration()
    }
    return [mimirConfigurationInstance: mimirConfigurationInstance]
  }
  
  
  def update() {
    def mimirConfigurationInstance = MimirConfiguration.findByIndexBaseDirectoryIsNotNull()
    if (mimirConfigurationInstance) {
      if (params.version) {
        def version = params.version.toLong()
        if (mimirConfigurationInstance.version > version) {

          mimirConfigurationInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [
            message(code: 'mimirConfiguration.label', default: 'MimirConfiguration')]
          as Object[], "Another user has updated this MimirConfiguration while you were editing")
          render(view: "edit", model: [mimirConfigurationInstance: mimirConfigurationInstance])
          return
        }
      }
    } else {
      mimirConfigurationInstance = new MimirConfiguration()
    }
    mimirConfigurationInstance.properties = params
    if (!mimirConfigurationInstance.hasErrors() && mimirConfigurationInstance.save(flush: true)) {
      flash.message = "${message(code: 'default.updated.message', args: [message(code: 'mimirConfiguration.label', default: 'MimirConfiguration'), mimirConfigurationInstance.id])}"
      redirect(action: "show")
    } else {
      render(view: "edit", model: [mimirConfigurationInstance: mimirConfigurationInstance])
    }
  }
}
