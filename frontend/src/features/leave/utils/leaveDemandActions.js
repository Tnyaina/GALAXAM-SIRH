export function canEditLeaveDemand(demand) {
  return demand?.availableActions?.EDIT?.allowed === true;
}
