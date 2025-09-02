/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  androidx.compose.runtime.internal.ComposableLambdaKt
 *  androidx.compose.runtime.internal.StabilityInferred
 *  com.intellij.ide.plugins.IdeaPluginDescriptor
 *  com.intellij.ide.plugins.PluginManagerCore
 *  com.intellij.ide.ui.LafManager
 *  com.intellij.ml.llm.matterhorn.ej.ui.ElectroJuniorToolWindowFactory$Companion
 *  com.intellij.ml.llm.matterhorn.ej.ui.ElectroJuniorToolWindowFactory$ToggleColumnModeAction
 *  com.intellij.ml.llm.matterhorn.ej.ui.ElectroJuniorToolWindowFactory$createToolWindowContent$$inlined$map$1
 *  com.intellij.ml.llm.matterhorn.ej.ui.JunieEventNotificationService
 *  com.intellij.ml.llm.matterhorn.ej.ui.UtilsKt
 *  com.intellij.ml.llm.matterhorn.ej.ui.toolwindow.JuniorToolWindowViewModel
 *  com.intellij.openapi.actionSystem.ActionGroup
 *  com.intellij.openapi.application.CoroutinesKt
 *  com.intellij.openapi.components.ComponentManager
 *  com.intellij.openapi.components.ServicesKt
 *  com.intellij.openapi.diagnostic.Logger
 *  com.intellij.openapi.extensions.PluginId
 *  com.intellij.openapi.project.DumbAware
 *  com.intellij.openapi.project.Project
 *  com.intellij.openapi.wm.ToolWindow
 *  com.intellij.openapi.wm.ToolWindowFactory
 *  com.intellij.openapi.wm.ToolWindowManager
 *  com.intellij.util.PlatformUtils
 *  kotlin.Metadata
 *  kotlin.Unit
 *  kotlin.collections.CollectionsKt
 *  kotlin.collections.SetsKt
 *  kotlin.coroutines.Continuation
 *  kotlin.coroutines.CoroutineContext
 *  kotlin.coroutines.intrinsics.IntrinsicsKt
 *  kotlin.jvm.functions.Function2
 *  kotlin.jvm.functions.Function3
 *  kotlin.jvm.internal.Intrinsics
 *  kotlin.jvm.internal.SourceDebugExtension
 *  kotlinx.coroutines.CoroutineScope
 *  kotlinx.coroutines.Dispatchers
 *  kotlinx.coroutines.flow.Flow
 *  kotlinx.coroutines.flow.FlowKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 *  org.jetbrains.jewel.bridge.ToolWindowExtensionsKt
 */
package com.intellij.ml.llm.matterhorn.ej.ui;

import androidx.compose.runtime.internal.ComposableLambdaKt;
import androidx.compose.runtime.internal.StabilityInferred;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.LafManager;
import com.intellij.ml.llm.matterhorn.ej.ui.ElectroJuniorToolWindowFactory;
import com.intellij.ml.llm.matterhorn.ej.ui.ElectroJuniorToolWindowFactory$createToolWindowContent$;
import com.intellij.ml.llm.matterhorn.ej.ui.JunieEventNotificationService;
import com.intellij.ml.llm.matterhorn.ej.ui.UtilsKt;
import com.intellij.ml.llm.matterhorn.ej.ui.toolwindow.JuniorToolWindowViewModel;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.CoroutinesKt;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ServicesKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.PlatformUtils;
import java.util.Set;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jewel.bridge.ToolWindowExtensionsKt;

@Metadata(mv={2, 1, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u0000 \u000f2\u00020\u00012\u00020\u0002:\u0003\u000f\u0010\u0011B\u0007\u00a2\u0006\u0004\b\u0003\u0010\u0004J\u001e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0096@\u00a2\u0006\u0002\u0010\u000bJ\u0018\u0010\f\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0007\u001a\u00020\bH\u0016\u00a8\u0006\u0012"}, d2={"Lcom/intellij/ml/llm/matterhorn/ej/ui/ElectroJuniorToolWindowFactory;", "Lcom/intellij/openapi/wm/ToolWindowFactory;", "Lcom/intellij/openapi/project/DumbAware;", "<init>", "()V", "manage", "", "toolWindow", "Lcom/intellij/openapi/wm/ToolWindow;", "toolWindowManager", "Lcom/intellij/openapi/wm/ToolWindowManager;", "(Lcom/intellij/openapi/wm/ToolWindow;Lcom/intellij/openapi/wm/ToolWindowManager;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "createToolWindowContent", "project", "Lcom/intellij/openapi/project/Project;", "Companion", "ToggleColumnModeAction", "OpenJunieOnceAfterInstallActivity", "ej-ui"})
@StabilityInferred(parameters=1)
@SourceDebugExtension(value={"SMAP\nElectroJuniorToolWindowFactory.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ElectroJuniorToolWindowFactory.kt\ncom/intellij/ml/llm/matterhorn/ej/ui/ElectroJuniorToolWindowFactory\n+ 2 services.kt\ncom/intellij/openapi/components/ServicesKt\n+ 3 logger.kt\ncom/intellij/openapi/diagnostic/LoggerKt\n+ 4 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 5 Transform.kt\nkotlinx/coroutines/flow/FlowKt__TransformKt\n+ 6 Emitters.kt\nkotlinx/coroutines/flow/FlowKt__EmittersKt\n+ 7 SafeCollector.common.kt\nkotlinx/coroutines/flow/internal/SafeCollector_commonKt\n*L\n1#1,228:1\n30#2,2:229\n30#2,2:231\n30#2,2:234\n24#3:233\n1#4:236\n49#5:237\n51#5:241\n46#6:238\n51#6:240\n105#7:239\n*S KotlinDebug\n*F\n+ 1 ElectroJuniorToolWindowFactory.kt\ncom/intellij/ml/llm/matterhorn/ej/ui/ElectroJuniorToolWindowFactory\n*L\n63#1:229,2\n69#1:231,2\n84#1:234,2\n71#1:233\n113#1:237\n113#1:241\n113#1:238\n113#1:240\n113#1:239\n*E\n"})
public final class ElectroJuniorToolWindowFactory
implements ToolWindowFactory,
DumbAware {
    @NotNull
    public static final Companion Companion = new Companion(null);
    public static final int $stable;
    @NotNull
    private static final Set<String> defaultThemes;
    @NotNull
    private static final String TOOLWINDOW_ID;

    @Nullable
    public Object manage(@NotNull ToolWindow toolWindow, @NotNull ToolWindowManager toolWindowManager, @NotNull Continuation<? super Unit> $completion) {
        Project project = toolWindow.getProject();
        Intrinsics.checkNotNullExpressionValue((Object)project, (String)"getProject(...)");
        ComponentManager $this$service$iv = (ComponentManager)project;
        boolean $i$f$service = false;
        Class<JunieEventNotificationService> serviceClass$iv = JunieEventNotificationService.class;
        Object object = $this$service$iv.getService(serviceClass$iv);
        if (object == null) {
            throw ServicesKt.serviceNotFoundError((ComponentManager)$this$service$iv, serviceClass$iv);
        }
        Object object2 = ((JunieEventNotificationService)object).manageIconInToolbar(toolWindow, $completion);
        if (object2 == IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            return object2;
        }
        return Unit.INSTANCE;
    }

    /*
     * WARNING - void declaration
     */
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        void $this$map$iv;
        String string;
        Intrinsics.checkNotNullParameter((Object)project, (String)"project");
        Intrinsics.checkNotNullParameter((Object)toolWindow, (String)"toolWindow");
        ComponentManager $this$service$iv = (ComponentManager)project;
        boolean $i$f$service = false;
        Class<JunieEventNotificationService> serviceClass$iv = JunieEventNotificationService.class;
        Object object = $this$service$iv.getService(serviceClass$iv);
        if (object == null) {
            throw ServicesKt.serviceNotFoundError((ComponentManager)$this$service$iv, serviceClass$iv);
        }
        JunieEventNotificationService notificationService = (JunieEventNotificationService)object;
        ElectroJuniorToolWindowFactory $this$thisLogger$iv = this;
        boolean $i$f$thisLogger = false;
        Logger logger = Logger.getInstance(ElectroJuniorToolWindowFactory.class);
        Intrinsics.checkNotNullExpressionValue((Object)logger, (String)"getInstance(...)");
        Logger logger2 = logger;
        if (UtilsKt.isWindows() && PlatformUtils.isJetBrainsClient()) {
            UtilsKt.showRemoteDevNotSupportedMessage((ToolWindow)toolWindow);
            return;
        }
        String themeName = LafManager.getInstance().getCurrentUIThemeLookAndFeel().getName();
        if (!defaultThemes.contains(themeName)) {
            UtilsKt.showThemeUnsupportedMessage((Project)project, (String)themeName);
            logger2.warn("Current theme " + themeName + " is not supported, Junie toolwindow will not be shown.");
        }
        ComponentManager $this$service$iv2 = (ComponentManager)project;
        boolean $i$f$service22 = false;
        Object serviceClass$iv2 = JuniorToolWindowViewModel.class;
        Object object2 = $this$service$iv2.getService(serviceClass$iv2);
        if (object2 == null) {
            throw ServicesKt.serviceNotFoundError((ComponentManager)$this$service$iv2, serviceClass$iv2);
        }
        JuniorToolWindowViewModel toolWindowViewModel = (JuniorToolWindowViewModel)object2;
        IdeaPluginDescriptor $i$f$service22 = PluginManagerCore.getPlugin((PluginId)PluginId.Companion.getId("org.jetbrains.junie"));
        if ($i$f$service22 != null && (serviceClass$iv2 = $i$f$service22.getVersion()) != null) {
            Object it = serviceClass$iv2;
            boolean bl = false;
            string = "v." + (String)it;
        } else {
            string = null;
        }
        String pluginVersion = string;
        ActionGroup toolWindowActions = UtilsKt.buildToolWindowActions(pluginVersion, (JuniorToolWindowViewModel)toolWindowViewModel, (Project)project);
        ToolWindowExtensionsKt.addComposeTab$default((ToolWindow)toolWindow, null, (boolean)false, (boolean)false, (Function3)((Function3)ComposableLambdaKt.composableLambdaInstance((int)-803555193, (boolean)true, (Object)new /* Unavailable Anonymous Inner Class!! */)), (int)7, null);
        toolWindow.setAdditionalGearActions(toolWindowActions);
        toolWindow.setTitleActions(CollectionsKt.listOf((Object)new ToggleColumnModeAction(toolWindow)));
        serviceClass$iv2 = (Flow)toolWindowViewModel.getAiProductTierNameStateFlow();
        boolean $i$f$map = false;
        void $this$unsafeTransform$iv$iv = $this$map$iv;
        boolean $i$f$unsafeTransform = false;
        boolean $i$f$unsafeFlow = false;
        FlowKt.launchIn((Flow)FlowKt.flowOn((Flow)FlowKt.onEach((Flow)((Flow)new createToolWindowContent$$inlined$map$1((Flow)$this$unsafeTransform$iv$iv)), (Function2)((Function2)new /* Unavailable Anonymous Inner Class!! */)), (CoroutineContext)CoroutinesKt.getEDT((Dispatchers)Dispatchers.INSTANCE)), (CoroutineScope)toolWindowViewModel.getScope());
    }

    public static final /* synthetic */ String access$getTOOLWINDOW_ID$cp() {
        return TOOLWINDOW_ID;
    }

    static {
        Object[] objectArray = new String[]{"Dark", "Light", "IntelliJ Light", "Light with Light Header", "High Contrast", "Darcula", "One Island Dark", "One Island Light", "Many Islands Dark", "Many Islands Light"};
        defaultThemes = SetsKt.setOf((Object[])objectArray);
        TOOLWINDOW_ID = "ElectroJunToolWindow";
    }
}
