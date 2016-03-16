ManagePagetreeCommand = (function ($, undefined) {

    var commands = [];

    return {
        addPage: function (name, parentId, newPageJstreeId) {
            commands.push({
                commandType: "addPage",
                name: name,
                newPageJstreeId: newPageJstreeId,
                parentId: parentId
            });
        },
        removePage: function (pageId) {
            commands.push({
                commandType: "removePage",
                pageId: pageId
            });
        },
        movePage: function (pageId, newParentId, newPosition) {
            commands.push({
                commandType: "movePage",
                pageId: pageId,
                newParentId: newParentId,
                newPosition: newPosition
            });
        },
        renamePage: function (pageId, newName) {
            commands.push({
                commandType: "renamePage",
                pageId: pageId,
                newName: newName
            });
        },
        send: function () {
            $.ajax({
                type: "POST",
                url: Confluence.getBaseUrl() + "/rest/pagetree/1.0/manage?space=" + AJS.params.spaceKey,
                data: JSON.stringify(commands),
                contentType: "application/json",
                dataType: "json",
                success: function (data) {
                    console.log(JSON.stringify(data));
                    commands = [];
                    if (data.status == 500)
                        alert(data.message);
                    else
                        location.reload();
                },
                error: function (data) {
                    data = JSON.parse(data.responseText);
                    console.log(JSON.stringify(data));
                    commands = [];
                    if (data.status == 500)
                        alert(data.message);
                    else
                        location.reload();
                }
            });
        }
    }
})(jQuery_1_11);