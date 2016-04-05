(function ($, undefined) {
    var dialog = "#add-page-tree-dialog";
    var tree = null;
    var can_create = true;
    var undoActions = null;

    function showDialog(payload) {
        require(['aui/inline-dialog2']);
        var root_page = payload.pageTree;
        can_create = payload.canCreate;
        undoActions = payload.lastChanges;
        eval("debugger;");

        if (!undoActions || (undoActions && undoActions.length == 0)) {
            document.getElementById("manage-pagetree-undo-button").disabled = true;
        }

        tree.jstree({
            "core": {
                "data": root_page,
                "check_callback": true,
                "dblclick_toggle": false,
                "themes": {
                    "stripes": true
                }
            },
            "plugins": [
                "dnd", "search", "state", "types"
            ],
            "types": {
                "#": {
                    "max_children": 1
                },
                "default": {
                    "icon": "icon-page"
                },
                "new": {
                    "icon": "icon-page-added"
                }
            },
            "dnd": {
                "is_draggable": function (nodes) {
                    return !nodes.filter(function (node) {
                        return node.id == root_page.id
                    }).length;
                }
            }
        });

        if (!can_create)
            $("#manage-pagetree-create-page-button").hide();

        AJS.dialog2(dialog).show();
    }

    function create_page(parent_id) {
        var jstree = tree.jstree(true);

        if (!can_create) return false;

        var child = jstree.create_node(parent_id, {
            text: "New page",
            type: "new"
        });
        if (child) {
            jstree.edit(child, null, function (page) {
                ManagePagetreeCommand.addPage(page.text, parent_id, page.id);
            });
        }
    }

    function rename_page(page_id) {
        var jstree = tree.jstree(true);

        var page = jstree.get_node(page_id);
        var oldName = page.text;
        if (!page.a_attr.data_canEdit) return false;

        jstree.edit(page, null, function (page) {
            ManagePagetreeCommand.renamePage(page_id, page.text, oldName);
        })
    }

    function remove_page(page_id) {
        var jstree = tree.jstree(true);

        var page = jstree.get_node(page_id);
        if (!page.a_attr.data_canRemove) return false;
        var pageName = page.text;

        jstree.delete_node(page_id);
        ManagePagetreeCommand.removePage(page_id, pageName);
    }

    AJS.toInit(function () {
        tree = $("#page-tree");

        var search_debounce = false;
        var search_f = function () {
            if (search_debounce) clearTimeout(search_debounce);
            search_debounce = setTimeout(function () {
                var v = $("#manage-pagetree-search-page").val();
                tree.jstree(true).search(v);
            }, 250);
        };
        var search_bar = $("#manage-pagetree-search-page");
        search_bar.change(search_f);
        search_bar.keyup(search_f);

        $("#manage-pagetree-create-page-button").click(function () {
            var selected = tree.jstree(true).get_selected();
            if (!selected.length) return false;
            create_page(selected[0]);
        });

        $("#manage-pagetree-rename-page-button").click(function () {
            var selected = tree.jstree(true).get_selected();
            if (!selected.length) return false;
            rename_page(selected[0]);
        });

        tree.on('dblclick', 'a.jstree-anchor', function (e) {
            var page_id = $(e.target).parent().attr("id");
            rename_page(page_id);
        });

        $("#manage-pagetree-remove-page-button").click(function () {
            var selected = tree.jstree(true).get_selected();
            if (!selected.length) return false;
            remove_page(selected[0]);
        });

        var set_jstree_event_listeners = function () {
            tree.on('move_node.jstree', function (e, data) {
                var parentName = tree.jstree(true).get_node(data.parent).text;
                ManagePagetreeCommand.movePage(data.node.id, data.parent, data.position, data.node.text, parentName);
            });

            tree.on("changed.jstree", function () {
                var jstree = tree.jstree(true);
                var selected = jstree.get_selected();

                $("#manage-pagetree-rename-page-button")[0].disabled = !selected.every(function (e) {
                    return jstree.get_node(e).a_attr.data_canEdit;
                });

                $("#manage-pagetree-remove-page-button")[0].disabled = !selected.every(function (e) {
                    return jstree.get_node(e).a_attr.data_canRemove;
                });
            });
        };
        set_jstree_event_listeners();

        //region link controls
        AJS.$("#add-pagetree-link-id").click(function (e) {
            e.preventDefault();

            $.ajax({
                type: "GET",
                url: Confluence.getBaseUrl() + "/rest/pagetree/1.0/pagetree?space=" + AJS.params.spaceKey,
                dataType: "json",
                success: showDialog,
                error: function () {
                    eval("debugger;")
                }
            });
        });
        //endregion

        //region dialog controls
        $(dialog + "-close-button").click(function (e) {
            e.preventDefault();
            AJS.dialog2(dialog).hide();
        });

        AJS.dialog2(dialog).on("hide", function () {
            tree.jstree(true).destroy();
            ManagePagetreeCommand.clearCommands();
            set_jstree_event_listeners();
        });

        $("#add-page-tree-dialog-really-undo-button").click(function (e) {
            e.preventDefault();
            $.ajax({
                type: "POST",
                url: Confluence.getBaseUrl() + "/rest/pagetree/1.0/revertLast?space=" + AJS.params.spaceKey,
                dataType: "json",
                error: function (data) {
                    data = JSON.parse(data.responseText);
                    console.log(JSON.stringify(data));
                    commands = [];
                    if (data.status == 500)
                        alert(data.message);
                    else
                        location.reload();
                },
                success: function() {
                    eval("debugger;");
                    location.reload()
                }
            });
        });

        $("#manage-pagetree-undo-button").click(function (e) {
            var actionsContainer = $("#pagetree-undo-action-list");
            var actions = undoActions.map(function(e) {
                switch (e.commandType) {
                    case "addPage":
                        return '<span class="aui-lozenge aui-lozenge-success">Add</span> "' + e.name + '"';
                    case "removePage":
                        return '<span class="aui-lozenge aui-lozenge-error">Remove</span> "' + e.name + '"';
                    case "movePage":
                        return '<span class="aui-lozenge aui-lozenge-current">Move</span> "' + e.name + '"';
                    case "renamePage":
                        return '<span class="aui-lozenge aui-lozenge-complete">Rename</span> "' + e.oldName +
                            '" -> "' + e.newName + '"';
                }
            }).join("</li><li>");

            if (actions != "") actions = "<li>" + actions + "</li>";
            actionsContainer.html(actions);
        });

        $(dialog + "-save-button").click(function (e) {
            var actionsContainer = $("#pagetree-action-list");
            var actions = ManagePagetreeCommand.getCommands().map(function(e) {
                switch (e.commandType) {
                    case "addPage":
                        return '<span class="aui-lozenge aui-lozenge-success">Add</span> "' + e.name + '"';
                    case "removePage":
                        return '<span class="aui-lozenge aui-lozenge-error">Remove</span> "' + e.name + '"';
                    case "movePage":
                        return '<span class="aui-lozenge aui-lozenge-current">Move</span> "' + e.name + '"';
                    case "renamePage":
                        return '<span class="aui-lozenge aui-lozenge-complete">Rename</span> "' + e.oldName +
                            '" -> "' + e.newName + '"';
                }
            }).join("</li><li>");

            if (actions != "") actions = "<li>" + actions + "</li>";
            actionsContainer.html(actions);
        });

        $(dialog + "-really-save-button").click(function (e) {
            e.preventDefault();
            ManagePagetreeCommand.send();
            AJS.dialog2(dialog).hide();
        });
        //endregion
    });
})(jQuery_1_11);
