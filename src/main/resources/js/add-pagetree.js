// callback runs when user exits the dialog
var showAddPageTreeDialog = (function ($) {
    var dialog = "#add-page-tree-dialog";
    var tree, callback;
    var forDeletion = [];

    function fixNames(pageTrees) {
        for (var j = 0; j < pageTrees.length; ++j) {
            var pageTree = pageTrees[j];

            pageTree.text = pageTree.text.split("<")[0];
            fixNames(pageTree.children);
        }
    }

    AJS.toInit(function () {
        tree = $("#page-tree");
        $(dialog + "-close-button").click(function (e) {
            e.preventDefault();
            AJS.dialog2(dialog).hide();
            tree.jstree().destroy();
            callback(false);
        });

        $(dialog + "-save-button").click(function (e) {
            e.preventDefault();
            AJS.dialog2(dialog).hide();

            var pageTrees = tree.jstree().get_json(null, {
                no_state: true,
                no_data: true
            });
            tree.jstree().destroy();
            fixNames(pageTrees);
            console.log(JSON.stringify(pageTrees));
            var modificationCommand = { root: pageTrees[0], forDeletion: forDeletion };
            callback(modificationCommand);
        });

        tree.on('click', 'button.add-node-btn', function (e) {
            var nodeId = $(e.target).parent().parent().attr("id");
            tree.jstree().create_node(
                nodeId,
                makeNode({
                    text: "newpage",
                    a_attr: { data_added: true },
                    icon: 'icon-page-added'
                }),
                "last",
                function (newnode) {
                    tree.jstree().open_node(
                        newnode.parent,
                        function () {
                            tree.jstree().edit(
                                newnode.id,
                                "newnode",
                                function (node) {
                                    ManagePagetreeCommand.addPage(node.text, nodeId, node.id);
                                    tree.jstree().rename_node(
                                        node.id,
                                        makeNode(node.text));
                                });
                        });
                });
        });

        tree.on('dblclick', 'a.jstree-anchor', function (e) {
            var nodeId = $(e.target).parent().attr("id");
            var node = tree.jstree().get_node(nodeId);
            if (node.parent == "#")
                return;
            var name = node.text.split('<')[0];
            tree.jstree().edit(nodeId, name, function (node) {
                ManagePagetreeCommand.renamePage(node.id, node.text);
                tree.jstree().rename_node(node.id, makeNode(node.text));
            });
        });

        tree.on('click', 'button.rem-node-btn', function (e) {
            var nodeId = $(e.target).parent().parent().attr("id");
            tree.jstree().delete_node(nodeId);
            ManagePagetreeCommand.removePage(nodeId);
        });

        tree.on('move_node.jstree', function (e, data) {
            console.log("moved node: " + data.node.id + ", parent: " + data.parent + ", pos: " + data.position);
            ManagePagetreeCommand.movePage(data.node.id, data.parent, data.position)
        });
    });

    function makeNode(node, isRoot) {
        if (typeof node === 'object') {
            var text = makeNode(node.text, isRoot);
            return {
                id: node.id,
                text: text,
                icon: node.icon || 'icon-page',
                a_attr: node.a_attr || { data_added: false },
                children: (Array.isArray(node.children))
                    ? node.children.map(function (child) { return makeNode(child, false) })
                    : []
            };
        } else if (typeof node == 'string') {
            var res = node + '<button class="add-node-btn">+</button>';
            if (!isRoot)
                res += '<button class="rem-node-btn">-</button>';
            return res;
        } else
            return node;
    }

    return function (rootpage, clbck) {
        if (clbck)
            callback = clbck;
        else
            callback = function () {};
        tree.jstree({
            "core": {
                "data": [makeNode(rootpage, true)],
                "check_callback": true,
                "dblclick_toggle": false,
                "themes": {
                    "stripes": true
                },
                "multiple": false
            },
            "plugins": ["dnd"],
            "dnd": {
                "is_draggable": function (nodes) {
                    return !nodes.filter(function (node) {
                        return node.id == rootpage.id
                    }).length;
                }
            }
        });

        AJS.dialog2(dialog).show();
    };
})(jQuery_1_11);

function setupPageTree(tree) {
    showAddPageTreeDialog(tree, function (modificationCommand) {
        eval("debugger;");
        ManagePagetreeCommand.send();
    });
}
AJS.toInit(function () {
    if (AJS.params.isSpaceAdmin) {
        AJS.$("#add-pagetree-link-id").click(function (e) {
            e.preventDefault();

            $.ajax({
                type: "GET",
                url: Confluence.getBaseUrl() + "/rest/pagetree/1.0/pagetree?space=" + AJS.params.spaceKey,
                dataType: "json",
                success: setupPageTree,
                error: function() {eval("debugger;")}
            });
        });
    } else {
        AJS.$("#add-pagetree-link-id").parent().remove();
    }
});