// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AutocreatingSingleSourceRootMoveDestination extends AutocreatingMoveDestination {
  private final VirtualFile mySourceRoot;

  public AutocreatingSingleSourceRootMoveDestination(PackageWrapper targetPackage, @NotNull VirtualFile sourceRoot) {
    super(targetPackage);
    mySourceRoot = sourceRoot;
  }

  @Override
  public @NotNull PackageWrapper getTargetPackage() {
    return myPackage;
  }

  @Override
  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  @Override
  public PsiDirectory getTargetIfExists(@NotNull PsiFile source) {
    return CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  @Override
  public PsiDirectory getTargetDirectory(@Nullable PsiDirectory source) throws IncorrectOperationException {
    return getDirectory();
  }

  @Override
  public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
    return getDirectory();
  }

  @Override
  public @Nullable @NlsContexts.DialogMessage String verify(PsiFile source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  @Override
  public String verify(PsiDirectory source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  @Override
  public String verify(PsiPackage aPackage) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  @Override
  public void analyzeModuleConflicts(final @NotNull Collection<? extends PsiElement> elements,
                                     @NotNull MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages) {
    RefactoringConflictsUtil.getInstance()
      .analyzeModuleConflicts(getTargetPackage().getManager().getProject(), elements, usages, mySourceRoot, conflicts);
  }

  @Override
  public boolean isTargetAccessible(@NotNull Project project, @NotNull VirtualFile place) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
    final Module module = ModuleUtilCore.findModuleForFile(place, project);
    if (mySourceRoot != null &&
        module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(mySourceRoot)) {
      return false;
    }
    return true;
  }

  PsiDirectory myTargetDirectory;
  private PsiDirectory getDirectory() throws IncorrectOperationException {
    if (myTargetDirectory == null) {
      myTargetDirectory = CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
    }
    return myTargetDirectory;
  }
}
