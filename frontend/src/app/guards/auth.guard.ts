import {inject} from '@angular/core';
import {CanActivateFn,CanActivateChildFn,Router} from '@angular/router';
import {map,catchError,of} from 'rxjs';
import {AuthService} from '../services/auth.service';
export const authGuard:CanActivateFn=()=>{
 const auth=inject(AuthService),router=inject(Router);
 return auth.atualizarSessao().pipe(map(()=>true),catchError(()=>of(router.createUrlTree(['/login']))));
};
export const permissaoGuard:CanActivateChildFn=(route)=>{
 const auth=inject(AuthService),router=inject(Router);
 return auth.atualizarSessao().pipe(map(()=>{
  const area=route.routeConfig?.path||'documentos';
  return auth.pode(area)?true:router.createUrlTree([auth.inicio]);
 }),catchError(()=>of(router.createUrlTree(['/login']))));
};
export const adminGuard:CanActivateFn=()=>inject(AuthService).admin?true:inject(Router).createUrlTree([inject(AuthService).inicio]);
