TemplateService = (function ($, undefined){
    var templatesUrl = Confluence.getBaseUrl() + "/rest/pagetree/1.0/templates";
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
        getTemplateById: function (templateId, templateType, callback) {
            if (templateType == "custom") {
                $.ajax({
                    type: "GET",
                    url: templatesUrl + "/" + templateId,
                    dataType: "json",
                    success: callback,
                    error: function () {
                        eval("debugger;")
                    }
                });
            } else {
                $.ajax({
                    type: "GET",
                    url: templatesUrl + "/blueprint/" + templateId,
                    dataType: "json",
                    success: callback,
                    error: function () {
                        eval("debugger;")
                    }
                });
            }
        },
        removeTemplateById: function (templateId, templateType, callback) {
            if (templateType == "custom") {
                $.ajax({
                    type: "DELETE",
                    url: templatesUrl + "/" + templateId,
                    success: callback,
                    error: function () {
                        eval("debugger;")
                    }
                });
            }
        },
        fromFile: function (file, callback) {
            $.ajax({
                type: "POST",
                url: templatesUrl,
                dataType: "json",
                contentType: "application/xml",
                data: file,
                success: callback,
                error: function () {
                    eval("debugger;");
                }
            });
        }
    };
})(jQuery_1_11);