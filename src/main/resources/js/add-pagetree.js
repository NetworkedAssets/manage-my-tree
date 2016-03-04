// callback runs when user exits the dialog
var showAddPageTreeDialog = (function ($) {

    var dialog = "#add-page-tree-dialog";
    var tree, callback;

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
            callback(pageTrees);
        });

        tree.on('click', 'button.add-node-btn', function (e) {
            var nodeId = $(e.target).parent().parent().attr("id");
            tree.jstree().create_node(
                nodeId,
                makeNode("newpage"),
                "last",
                function (newnode) {
                    tree.jstree().open_node(
                        newnode.parent,
                        function () {
                            tree.jstree().edit(
                                newnode.id,
                                "newnode",
                                function (node) {
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
                tree.jstree().rename_node(node.id, makeNode(node.text));
            });
        });

        tree.on('click', 'button.rem-node-btn', function (e) {
            var nodeId = $(e.target).parent().parent().attr("id");
            tree.jstree().delete_node(nodeId);
        });
    });

    function makeNode(node, isRoot) {
        if (typeof node === 'object') {
            var text = makeNode(node.text, isRoot);
            return {
                id: node.id,
                text: text,
                icon: 'icon-page',
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
            callback = function () {
            };
        var res = tree.jstree({
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
    showAddPageTreeDialog(tree, function (pageTrees) {
        if (pageTrees) {
            $.ajax({
                type: "POST",
                url: Confluence.getBaseUrl() + "/rest/pagetree/1.0/manage?space=" + AJS.params.spaceKey,
                data: JSON.stringify(pageTrees),
                contentType: "application/json",
                dataType: "json",
                success: function (data) {
                    console.log(JSON.stringify(data));
                    if (data.status == 500)
                        alert(data.message);
                    else
                        location.reload();
                },
                error: function (data) {
                    data = JSON.parse(data.responseText);
                    console.log(JSON.stringify(data));
                    if (data.status == 500)
                        alert(data.message);
                    else
                        location.reload();
                }
            });
        }
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
                error: function() {eval("debugger")}
            });
        });
    } else {
        AJS.$("#add-pagetree-link-id").parent().remove();
    }
});