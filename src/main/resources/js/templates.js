TemplateService = (function ($, undefined){
    var templatesUrl = Confluence.getBaseUrl() + "/rest/pagetree/1.0/templates"
    return {
        getTemplateList: function (callback) {
            $.ajax({
                type: "GET",
                url: templatesUrl+"?withBody=false",
                dataType: "json",
                success: callback,
                error: function () {
                    eval("debugger;")
                }
            });
        },
        getTemplateById: function (templateId, callback) {
            $.ajax({
                type: "GET",
                url: templatesUrl+"/" + templateId,
                dataType: "json",
                success: callback,
                error: function () {
                    eval("debugger;")
                }
            });
        }
    };
})(jQuery_1_11);