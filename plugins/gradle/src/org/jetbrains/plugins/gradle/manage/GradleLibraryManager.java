package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 2/15/12 11:32 AM
 */
public class GradleLibraryManager {

  @NotNull private final PlatformFacade myPlatformFacade;

  public GradleLibraryManager(@NotNull PlatformFacade platformFacade) {
    myPlatformFacade = platformFacade;
  }

  public void importLibraries(@NotNull Collection<? extends GradleLibrary> libraries, @NotNull Project project) {
    for (GradleLibrary library : libraries) {
      importLibrary(library, project);
    }
  }
  
  public void importLibrary(@NotNull final GradleLibrary library, @NotNull final Project project) {
    Map<OrderRootType, Collection<File>> libraryFiles = new HashMap<OrderRootType, Collection<File>>();
    for (LibraryPathType pathType : LibraryPathType.values()) {
      final Set<String> paths = library.getPaths(pathType);
      if (paths.isEmpty()) {
        continue;
      }
      libraryFiles.put(Lazy.LIBRARY_ROOT_MAPPINGS.get(pathType), ContainerUtil.map(paths, new NotNullFunction<String, File>() {
        @NotNull
        @Override
        public File fun(String path) {
          return new File(path);
        }
      }));
    }
    importLibrary(library.getName(), libraryFiles, project);
  }

  public void importLibrary(@NotNull final String libraryName,
                            @NotNull final Map<OrderRootType, ? extends Collection<File>> libraryFiles,
                            @NotNull final Project project)
  {
    GradleUtil.executeProjectChangeAction(project, libraryName, new Runnable() {
      @Override
      public void run() {
        // Is assumed to be called from the EDT.
        final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        final LibraryTable.ModifiableModel projectLibraryModel = libraryTable.getModifiableModel();
        final Library intellijLibrary;
        try {
          intellijLibrary = projectLibraryModel.createLibrary(libraryName);
        }
        finally {
          projectLibraryModel.commit();
        }
        final Library.ModifiableModel libraryModel = intellijLibrary.getModifiableModel();
        try {
          registerPaths(libraryFiles, libraryModel, libraryName);
        }
        finally {
          libraryModel.commit();
        }
      }
    });
  }

  private static void registerPaths(@NotNull final Map<OrderRootType, ? extends Collection<File>> libraryFiles,
                                    @NotNull Library.ModifiableModel model,
                                    @NotNull String libraryName)
  {
    for (Map.Entry<OrderRootType, ? extends Collection<File>> entry : libraryFiles.entrySet()) {
      for (File file : entry.getValue()) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (virtualFile == null) {
          //GradleLog.LOG.warn(
          //  String.format("Can't find %s of the library '%s' at path '%s'", entry.getKey(), libraryName, file.getAbsolutePath())
          //);
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, entry.getKey());
        }
        else {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
          if (jarRoot == null) {
            GradleLog.LOG.warn(String.format(
              "Can't parse contents of the jar file at path '%s' for the library '%s''", file.getAbsolutePath(), libraryName
            ));
            continue;
          }
          model.addRoot(jarRoot, entry.getKey());
        }
      }
    }
  }

  public void removeLibraries(@NotNull List<? extends Library> libraries) {
    // TODO den implement
    //LibraryTable table = library.getTable();
    //table.removeLibrary(library);
  }
  
  
  private static class Lazy {
    static final Map<LibraryPathType, OrderRootType> LIBRARY_ROOT_MAPPINGS
      = new EnumMap<LibraryPathType, OrderRootType>(LibraryPathType.class);
    static {
      LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
      LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
      LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.DOC, JavadocOrderRootType.getInstance());
      assert LibraryPathType.values().length == LIBRARY_ROOT_MAPPINGS.size();
    }
  }
}
