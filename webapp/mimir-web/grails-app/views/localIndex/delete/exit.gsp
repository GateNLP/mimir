<% 
    flash.message = "Local index ${localIndexInstance.name} deleted"
    response.sendRedirect(g.createLink(controller:'mimirStaticPages', action:'admin').toString())
%>
