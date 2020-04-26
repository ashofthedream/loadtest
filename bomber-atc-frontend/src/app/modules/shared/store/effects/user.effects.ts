import { Injectable } from "@angular/core";
import { Actions, Effect, ofType } from "@ngrx/effects";
import { GetUser, GetUserSuccess, UserAction } from "../actions/user.actions";
import { map, switchMap } from "rxjs/operators";
import { Observable } from "rxjs";
import { UserService } from "../../services/user.service";

@Injectable()
export class UserEffects {

  constructor(private readonly userService: UserService,
              private readonly actions: Actions) {
  }

  @Effect()
  public getUser(): Observable<GetUserSuccess> {
    return this.actions
        .pipe(
            ofType<GetUser>(UserAction.GetUser),
            switchMap(act => this.userService.getUser()),
            map(user => new GetUserSuccess(user))
        );
  }
}