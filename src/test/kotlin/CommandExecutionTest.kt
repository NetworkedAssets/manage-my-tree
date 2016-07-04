import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.spaces.Space
import com.atlassian.confluence.spaces.SpaceManager
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.user.ConfluenceUser
import com.atlassian.sal.api.pluginsettings.PluginSettings
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory
import com.atlassian.sal.api.user.UserKey
import com.networkedassets.plugins.managemytree.JsonMessage
import com.networkedassets.plugins.managemytree.PageTreeService
import com.networkedassets.plugins.managemytree.commands.AddPage
import com.networkedassets.plugins.managemytree.commands.RemovePage
import com.networkedassets.plugins.managemytree.commands.RenamePage
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.junit.JUnitSpekRunner
import org.junit.runner.RunWith

@RunWith(JUnitSpekRunner::class)
class CommandExecutionTest : Spek({
    describe("page-tree management service") {
        given("all the dependant services") {
            val space = Space("TST")
            val grandparent = Page().apply { title = "grandparent"; id = 0; this.space = space }
            val parent = Page().apply { title = "parent"; id = 1; this.space = space }
            grandparent.addChild(parent)
            val child = Page().apply { title = "child"; id = 2; this.space = space }
            parent.addChild(child)

            val user: ConfluenceUser = mock()
            When calling user.key `it returns` UserKey("userkey")
            AuthenticatedUserThreadLocal.set(user)

            val pageManager: PageManager = mock()
            addPageToMock(pageManager, grandparent)
            addPageToMock(pageManager, parent)
            addPageToMock(pageManager, child)

            var addedPage: Page? = null
            When calling pageManager.saveContentEntity(any(), any()) `it answers` { addedPage = it.arguments[0] as Page }
            When calling pageManager.renamePage(any(), any()) `it answers` {
                val page = it.arguments[0] as Page
                page.title = it.arguments[1] as String
            }

            val permissionManager: PermissionManager = mock()
            When calling permissionManager.hasPermission(eq(user), any<Permission>(), any<Any>()) `it returns` true
            When calling permissionManager.hasCreatePermission(eq(user), any(), any<Any>()) `it returns` true

            val spaceManager: SpaceManager = mock()
            When calling spaceManager.getSpace("TST") `it returns` space

            val pluginSettings: PluginSettings = mock()

            val pluginSettingsFactory: PluginSettingsFactory = mock()
            When calling pluginSettingsFactory.createSettingsForKey(any()) `it returns` pluginSettings

            val pageTreeService = PageTreeService(pageManager, permissionManager, spaceManager, pluginSettingsFactory)

            on("receiving valid AddPage request") {
                val resp = pageTreeService.managePages("TST", listOf(AddPage("Test Name", "j_1_0", "1")))

                it("should respond with 200") {
                    resp.entity `should equal` JsonMessage(status = 200, message = "Success")
                }

                describe("added page") {
                    it("should exist") {
                        addedPage `should not be` null
                    }

                    it("""should have title "Test Name"""") {
                        addedPage?.title `should equal` "Test Name"
                    }

                    it("should have the proper parent") {
                        addedPage?.parent `should be` parent
                    }

                    it("should be in the proper space") {
                        addedPage?.spaceKey `should equal` "TST"
                    }
                }
            }

            on("receiving a valid RenamePage request") {
                val resp = pageTreeService.managePages("TST", listOf(RenamePage("1", "new name")))

                it("should respond with 200") {
                    resp.entity `should equal` JsonMessage(status = 200, message = "Success")
                }

                describe("affected page") {
                    it("""should have title "new name"""") {
                        parent.title `should equal` "new name"
                    }
                }
            }

            on("receiving a valid RemovePage request") { // TODO: fix this test to not remove a root page. Also, add a proper error message when user tries to do that.
                val resp = pageTreeService.managePages("TST", listOf(RemovePage("1")))

                it("should respond with 200") {
                    resp.entity `should equal` JsonMessage(status = 200, message = "Success")
                }

                it("should trash the page and its children") {
                    Verify on pageManager that pageManager.trashPage(parent) was called
                    Verify on pageManager that pageManager.trashPage(child) was called
                }
            }
        }
    }
})

private fun addPageToMock(pageManagerMock: PageManager, page: Page) {
    When calling pageManagerMock.getPage(page.id) `it returns` page
    When calling pageManagerMock.getAbstractPage(page.id) `it returns` page
}

