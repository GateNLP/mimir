<% 
    flash.message = "Error: ${rootCauseException.message}. Local index ${localIndexInstance.name} not deleted"
    response.sendRedirect(g.createLink(controller:"indexAdmin", action:"admin", 
            params:[indexId:localIndexInstance.indexId]).toString())
%>
