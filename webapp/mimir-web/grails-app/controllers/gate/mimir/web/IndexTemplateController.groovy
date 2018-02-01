package gate.mimir.web

import org.springframework.dao.DataIntegrityViolationException

class IndexTemplateController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

    def index() {
        redirect(action: "list", params: params)
    }

    def list(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        [indexTemplateInstanceList: IndexTemplate.list(params), indexTemplateInstanceTotal: IndexTemplate.count()]
    }

    def create() {
        [indexTemplateInstance: new IndexTemplate(params)]
    }

    def save() {
        def indexTemplateInstance = new IndexTemplate(params)
        if (!indexTemplateInstance.save(flush: true)) {
            render(view: "create", model: [indexTemplateInstance: indexTemplateInstance])
            return
        }

        flash.message = message(code: 'default.created.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), indexTemplateInstance.id])
        redirect(action: "show", id: indexTemplateInstance.id)
    }

    def show(Long id) {
        def indexTemplateInstance = IndexTemplate.get(id)
        if (!indexTemplateInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), id])
            redirect(action: "list")
            return
        }

        [indexTemplateInstance: indexTemplateInstance]
    }

    def edit(Long id) {
        def indexTemplateInstance = IndexTemplate.get(id)
        if (!indexTemplateInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), id])
            redirect(action: "list")
            return
        }

        [indexTemplateInstance: indexTemplateInstance]
    }

    def update(Long id, Long version) {
        def indexTemplateInstance = IndexTemplate.get(id)
        if (!indexTemplateInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), id])
            redirect(action: "list")
            return
        }

        if (version != null) {
            if (indexTemplateInstance.version > version) {
                indexTemplateInstance.errors.rejectValue("version", "default.optimistic.locking.failure",
                          [message(code: 'indexTemplate.label', default: 'IndexTemplate')] as Object[],
                          "Another user has updated this IndexTemplate while you were editing")
                render(view: "edit", model: [indexTemplateInstance: indexTemplateInstance])
                return
            }
        }

        indexTemplateInstance.properties = params

        if (!indexTemplateInstance.save(flush: true)) {
            render(view: "edit", model: [indexTemplateInstance: indexTemplateInstance])
            return
        }

        flash.message = message(code: 'default.updated.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), indexTemplateInstance.id])
        redirect(action: "show", id: indexTemplateInstance.id)
    }

    def delete(Long id) {
        def indexTemplateInstance = IndexTemplate.get(id)
        if (!indexTemplateInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), id])
            redirect(action: "list")
            return
        }

        try {
            indexTemplateInstance.delete(flush: true)
            flash.message = message(code: 'default.deleted.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), id])
            redirect(action: "list")
        }
        catch (DataIntegrityViolationException e) {
            flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'indexTemplate.label', default: 'IndexTemplate'), id])
            redirect(action: "show", id: id)
        }
    }
}
