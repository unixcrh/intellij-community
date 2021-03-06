package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/11/12
 */
public class ModuleExcludeIndexImpl implements ModuleExcludeIndex {
  private final Set<File> myExcludedRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  private final Set<File> myContentRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  private final Map<JpsModule, List<File>> myModuleToExcludesMap = new THashMap<JpsModule, List<File>>();

  public ModuleExcludeIndexImpl(JpsModel model) {
    final Collection<JpsModule> allModules = model.getProject().getModules();
    for (final JpsModule module : allModules) {
      final List<File> moduleExcludes = new ArrayList<File>();
      for (String url : module.getExcludeRootsList().getUrls()) {
        moduleExcludes.add(JpsPathUtil.urlToFile(url));
      }
      JpsJavaModuleExtension moduleExtension = JpsJavaExtensionService.getInstance().getModuleExtension(module);
      if (moduleExtension != null && !moduleExtension.isInheritOutput() && moduleExtension.isExcludeOutput()) {
        String outputUrl = moduleExtension.getOutputUrl();
        if (outputUrl != null) {
          moduleExcludes.add(JpsPathUtil.urlToFile(outputUrl));
        }
        String testOutputUrl = moduleExtension.getTestOutputUrl();
        if (testOutputUrl != null) {
          moduleExcludes.add(JpsPathUtil.urlToFile(testOutputUrl));
        }
      }
      myModuleToExcludesMap.put(module, moduleExcludes);
      myExcludedRoots.addAll(moduleExcludes);
    }

    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(model.getProject());
    if (projectExtension != null) {
      String url = projectExtension.getOutputUrl();
      if (url != null) {
        myExcludedRoots.add(JpsPathUtil.urlToFile(url));
      }
    }

    Map<File, JpsModule> contentToModule = new HashMap<File, JpsModule>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        contentToModule.put(contentRoot, module);
      }
    }
    List<File> parents = new ArrayList<File>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        File parent = contentRoot.getParentFile();
        JpsModule parentModule = null;
        parents.clear();
        while (parent != null) {
          parents.add(parent);
          if (contentToModule.containsKey(parent)) {
            parentModule = contentToModule.get(parent);
            break;
          }
          parent = parent.getParentFile();
        }
        if (parentModule != null) {
          myModuleToExcludesMap.get(parentModule).add(contentRoot);
        }
        else {
          myContentRoots.add(contentRoot);
        }
        for (File file : parents) {
          contentToModule.put(file, parentModule);
        }
      }
    }
    for (List<File> files : myModuleToExcludesMap.values()) {
      ((ArrayList<File>)files).trimToSize();
    }
  }

  @Override
  public boolean isExcluded(File file) {
    return JpsPathUtil.isUnder(myExcludedRoots, file);
  }

  @Override
  public boolean isInContent(File file) {
    return JpsPathUtil.isUnder(myContentRoots, file);
  }

  @Override
  public Collection<File> getModuleExcludes(JpsModule module) {
    return myModuleToExcludesMap.get(module);
  }
}
