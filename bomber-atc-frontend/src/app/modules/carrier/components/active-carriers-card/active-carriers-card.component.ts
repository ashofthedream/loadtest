import { Component } from '@angular/core';
import { Store } from "@ngrx/store";
import { AtcState } from "../../../shared/store/state/atc.state";
import { activeCarriers } from "../../store/selectors/carrier.selector";

@Component({
  selector: 'carriers-active-card',
  templateUrl: './active-carriers-card.component.html'
})
export class ActiveCarriersCardComponent {

  carriers = this.store.select(activeCarriers);

  constructor(private readonly store: Store<AtcState>) {
  }
}
