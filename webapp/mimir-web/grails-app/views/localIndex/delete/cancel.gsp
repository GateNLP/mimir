<% 
    flash.message = "Local index ${localIndexInstance.name} not deleted"
    response.sendRedirect(g.createLink(action:'show', id:localIndexInstance.id).toString())
%>
