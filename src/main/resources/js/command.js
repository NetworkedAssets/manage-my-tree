ManagePagetreeCommand = {
    addPage: function (name, parentId) {
        return {
            "commandType": "addPage",
            "name": name,
            "parentId": parentId
        }
    },
    removePage: function (pageId) {
        return {
            "commandType": "removePage",
            "pageId": pageId
        }
    },
    movePage: function (pageId, newParentId, newPosition) {
        return {
            "commandType": "movePage",
            "pageId": pageId,
            "newParentId": newParentId,
            "newPosition": newPosition
        }
    },
    renamePage: function (pageId, newName) {
        return {
            "commandType": "renamePage",
            "pageId": pageId,
            "newName": newName
        }
    }
};