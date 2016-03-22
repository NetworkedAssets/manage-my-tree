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
        removePage: function (pageId, pageName) {
            commands.push({
                commandType: "removePage",
                pageId: pageId,
                name: pageName
            });
        },
        movePage: function (pageId, newParentId, newPosition, nodeName, parentName) {
            commands.push({
                commandType: "movePage",
                pageId: pageId,
                newParentId: newParentId,
                newPosition: newPosition,
                name: nodeName,
                parentName: parentName
            });
        },
        renamePage: function (pageId, newName, oldName) {
            if (newName == oldName) return;
            commands.push({
                commandType: "renamePage",
                pageId: pageId,
                newName: newName,
                oldName: oldName
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
        },
        getCommands: function () {
            return commands;
        },
        clearCommands: function () {
            commands = [];
        }
    }
})(jQuery_1_11);