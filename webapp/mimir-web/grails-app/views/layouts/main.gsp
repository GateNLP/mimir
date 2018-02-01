<html>
    <head>
        <title><g:layoutTitle default="Mimir" /></title>
        <asset:stylesheet src="main.css" />
        <link rel="shortcut icon" href="${assetPath(src:'mimir-favicon.ico')}?v=1" type="image/x-icon" />
        <g:layoutHead />
        <asset:javascript src="application.js" />
    </head>
    <body>
        <div id="spinner" class="spinner" style="display:none;">
            <img src="${resource(dir:'images',file:'spinner.gif')}" alt="Spinner" />
        </div>	
        <div class="logo"><mimir:logo/></div>	
        <g:layoutBody />		
    </body>	
</html>
