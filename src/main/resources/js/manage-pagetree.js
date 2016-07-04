(function ($, undefined) {
    var dialog = "#add-page-tree-dialog";
    var tree = null;
    var template_tree = null;
    var can_create = true;
    var undoActions = null;

    function showDialog(payload) {
        require(['aui/inline-dialog2']);
        var root_page = payload.pageTree;
        can_create = payload.canCreate;
        undoActions = payload.lastChanges;

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
                },
                "template": {
                    "icon": "icon-page-template"
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

        init_template_tree({
            text: "test",
            type: "template"
        });

        if (!can_create)
            $("#manage-pagetree-create-page-button").hide();

        AJS.dialog2(dialog).show();
    }

    function init_template_tree(data) {
        template_tree.jstree({
            "core": {
                "data": data,
                "check_callback": function (operation, node, node_parent, node_position, more) {
                    if (operation === "move_node") {
                        return node.type === "template";
                    }
                    return true;
                },
                "dblclick_toggle": false,
                "themes": {
                    "stripes": true
                }
            },
            "plugins": [
                "dnd", "search", "state", "types"
            ],
            "types": {
                "template": {
                    "icon": "icon-page-template"
                }
            },
            "dnd": {
                "is_draggable": function (nodes) {
                    return true;
                }
            }
        });
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

    function insert_template(parent_id, template) {
        var jstree = tree.jstree(true);

        if (!can_create) return false;

        console.log(template);
        var jstree_ids = {};
        for (var i = 0; i < template.outlines.length; ++i) {
            insert_tree(jstree, parent_id, template.outlines[i], jstree_ids);
        }
        jstree.open_node(parent_id);

        ManagePagetreeCommand.insertTemplate(template.id, parent_id, jstree_ids, template.name);

        console.log(JSON.stringify(ManagePagetreeCommand.getCommands()));
    }

    function insert_tree(jstree, parent_id, tree, jstree_ids) {
        var child = jstree.create_node(parent_id, {
            text: tree.title,
            type: "template"
        });
        jstree_ids[tree.id] = child;
        for (var i = 0; i < tree.children.length; ++i) {
            insert_tree(jstree, child, tree.children[i], jstree_ids);
        }
        jstree.open_node(child);
    }

    function rename_page(page_id) {
        var jstree = tree.jstree(true);

        var page = jstree.get_node(page_id);
        var oldName = page.text;
        if (!(page.type == "new" || page.a_attr.data_canEdit)) return false;

        jstree.edit(page, null, function (page) {
            ManagePagetreeCommand.renamePage(page_id, page.text, oldName);
        })
    }

    function remove_page(page_id) {
        var jstree = tree.jstree(true);

        var page = jstree.get_node(page_id);
        if (!(page.type == "new" || page.a_attr.data_canRemove)) return false;
        var pageName = page.text;

        jstree.delete_node(page_id);
        ManagePagetreeCommand.removePage(page_id, pageName);
    }

    function dialog_size(size) {
        var dialog = $("#add-page-tree-dialog");
        var classes = dialog.attr("class").split(/\s+/);
        var dialog_size_class;
        for (var i = 0; i < classes.length; ++i) {
            if (classes[i].indexOf("aui-dialog2-") != -1) {
                dialog_size_class = classes[i];
            }
        }
        dialog.removeClass(dialog_size_class).addClass("aui-dialog2-" + size);
    }

    function hide_template_tree() {
        template_tree.parent().hide();
        tree.parent().removeClass('pagetree-half');
        dialog_size('medium');
    }

    function show_template_tree() {
        template_tree.parent().show();
        tree.parent().addClass('pagetree-half');
        dialog_size('xlarge');
    }

    function command_list_to_html(actions) {
        return actions.map(function (e) {
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
                case "insertTemplate":
                    return '<span class="aui-lozenge aui-lozenge-success">Insert template</span> "' +
                        e.name + '"';
            }
        }).join("</li><li>");
    }

    AJS.toInit(function () {
        tree = $("#page-tree");
        template_tree = $("#template-page-tree");
        hide_template_tree();

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

        var span_remove = '<span class="template-remove aui-icon aui-icon-small aui-iconfont-close-dialog" ' +
            'style="float: right; top: 2px; margin-left: 5px"></span>';
        var span_sure = '<span class="template-remove-sure" ' +
            'style="float: right; text-decoration: underline; margin-left: 5px">Remove?</span>';

        $("#manage-pagetree-insert-template-button").click(function () {
            TemplateService.getTemplateList(function (templates) {
                // console.log(templates);
                var template_list = $("#pagetree-template-list-inner");
                var blueprint_template_list = $("#pagetree-template-list-blueprint-inner");
                template_list.empty();
                blueprint_template_list.empty();
                for (var i = 0; i < templates.length; ++i) {
                    var id = templates[i].id;
                    if (id.templateType == "custom") {
                        template_list.append(
                            '<li data-template-name="' + templates[i].name + '" ' +
                                'data-template-id="' + id.customOutlineId + '" ' +
                                'data-template-type="' + id.templateType + '">' +
                                '<a href="#">' +
                                    templates[i].name +
                                    span_remove +
                                '</a>' +
                            '</li>'
                        );
                    } else { // fromBlueprint
                        blueprint_template_list.append(
                            '<li data-template-name="' + templates[i].name + '" ' +
                                'data-template-id="' + id.spaceBlueprintId + '" ' +
                                'data-template-type="' + id.templateType + '">' +
                                '<a href="#">' +
                                    templates[i].name +
                                '</a>' +
                            '</li>'
                        );
                    }
                }
            });
        });

        var template_list = $("#pagetree-template-list-inner");

        template_list.on("click", "span.template-remove", function (e) {
            e.stopPropagation();
            var templateAnchorElem = e.currentTarget.parentElement;
            $(e.currentTarget).remove();
            $(templateAnchorElem).append(span_sure);
        });

        template_list.on("mouseout", "span.template-remove-sure", function (e) {
            e.stopPropagation();
            var templateAnchorElem = e.currentTarget.parentElement;
            $(e.currentTarget).remove();
            $(templateAnchorElem).append(span_remove);
        });

        template_list.on("click", "span.template-remove-sure", function (e) {
            e.stopPropagation();
            var templateElem = e.currentTarget.parentElement.parentElement;
            TemplateService.removeTemplateById(
                templateElem.dataset.templateId,
                templateElem.dataset.templateType,
                function () {
                    $(templateElem).remove();
                }
            );
        });

        var all_templates = $("#pagetree-template-list-inner,#pagetree-template-list-blueprint-inner");

        all_templates.on("click", "li", function (e) {
            var templateElem = e.currentTarget;
            TemplateService.getTemplateById(
                templateElem.dataset.templateId,
                templateElem.dataset.templateType,
                function (template) {
                    show_template_tree();
                    template_tree.jstree(true).destroy();
                    init_template_tree(null);
                    var jstree = template_tree.jstree(true);

                    for (var i = 0; i < template.outlines.length; ++i) {
                        insert_tree(jstree, "#", template.outlines[i], {});
                    }
                    jstree.open_node("#");
                }
            );
        });

        $("#template-upload-input").on('change', function(e) {
            var file = e.target.files[0];
            if (file) {
                var fr = new FileReader();
                fr.onload = function (e) {
                    TemplateService.fromFile(e.target.result, function() {
                        document.getElementById("manage-pagetree-insert-template-button").click();
                    });
                };
                fr.readAsText(file);
            }
            this.value = null;
            return false;
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
            for (var i = 0; i < selected.length; ++i) {
                remove_page(selected[i]);
            }
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
                    var node = jstree.get_node(e);
                    return node.type == "new" || node.a_attr.data_canEdit;
                });

                $("#manage-pagetree-remove-page-button")[0].disabled = !selected.every(function (e) {
                    var node = jstree.get_node(e);
                    return node.type == "new" || node.a_attr.data_canRemove;
                });
            });
        };
        set_jstree_event_listeners();

        //region link controls
        $("#add-pagetree-link-id").click(function (e) {
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
            hide_template_tree();
            tree.jstree(true).destroy();
            template_tree.jstree(true).destroy();
            ManagePagetreeCommand.clearCommands();
            set_jstree_event_listeners();
        });

        $("#add-page-tree-dialog-really-undo-button").click(function (e) {
            e.preventDefault();
            $.ajax({
                type: "POST",
                url: Confluence.getBaseUrl() + "/rest/pagetree/1.0/revertLast?space=" + AJS.params.spaceKey,
                dataType: "json",
                contentType: "application/json",
                headers: {"X-Atlassian-Token": "nocheck"},
                error: function (data) {
                    data = JSON.parse(data.responseText);
                    console.log(JSON.stringify(data));
                    // commands = [];
                    if (data.status == 500)
                        alert(data.message);
                    else
                        location.reload();
                },
                success: function () {
                    location.reload()
                }
            });
        });

        $("#manage-pagetree-undo-button").click(function () {
            var actionsContainer = $("#pagetree-undo-action-list");
            var actions = command_list_to_html(undoActions);

            if (actions != "") actions = "<li>" + actions + "</li>";
            actionsContainer.html(actions);
        });

        $(dialog + "-save-button").click(function () {
            var actionsContainer = $("#pagetree-action-list");
            var actions = command_list_to_html(ManagePagetreeCommand.getCommands());

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
